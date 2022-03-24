// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.PomManager
import com.intellij.pom.PomModelAspect
import com.intellij.pom.event.PomModelEvent
import com.intellij.pom.event.PomModelListener
import com.intellij.pom.tree.TreeAspect
import com.intellij.pom.tree.events.TreeChangeEvent
import com.intellij.pom.tree.events.impl.ChangeInfoImpl
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findTopmostParentInFile
import com.intellij.psi.util.findTopmostParentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

interface PureKotlinOutOfCodeBlockModificationListener {
    fun kotlinFileOutOfCodeBlockChanged(file: KtFile, physical: Boolean)
}

class PureKotlinCodeBlockModificationListener(project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project): PureKotlinCodeBlockModificationListener = project.getServiceSafe()

        private fun isReplLine(file: VirtualFile): Boolean = file.getUserData(KOTLIN_CONSOLE_KEY) == true

        private fun incFileModificationCount(file: KtFile) {
            val tracker = file.getUserData(PER_FILE_MODIFICATION_TRACKER)
                ?: file.putUserDataIfAbsent(PER_FILE_MODIFICATION_TRACKER, SimpleModificationTracker())

            tracker.incModificationCount()
        }

        private fun inBlockModifications(elements: Array<ASTNode>): List<KtElement> {
            if (elements.any { !it.psi.isValid }) return emptyList()

            // When a code fragment is reparsed, Intellij doesn't do an AST diff and considers the entire
            // contents to be replaced, which is represented in a POM event as an empty list of changed elements

            return elements.map { element ->
                val modificationScope = getInsideCodeBlockModificationScope(element.psi) ?: return emptyList()
                modificationScope.blockDeclaration
            }
        }

        private fun isSpecificChange(changeSet: TreeChangeEvent, precondition: (ASTNode?) -> Boolean): Boolean =
            changeSet.changedElements.all { changedElement ->
                val changesByElement = changeSet.getChangesByElement(changedElement)
                changesByElement.affectedChildren.all { affectedChild ->
                    precondition(affectedChild) && changesByElement.getChangeByChild(affectedChild).let { changeByChild ->
                        if (changeByChild is ChangeInfoImpl) {
                            val oldChild = changeByChild.oldChildNode
                            precondition(oldChild)
                        } else false
                    }
                }
            }

        private inline fun isCommentChange(changeSet: TreeChangeEvent): Boolean = isSpecificChange(changeSet) { it is PsiComment || it is KDoc }

        private inline fun isFormattingChange(changeSet: TreeChangeEvent): Boolean = isSpecificChange(changeSet) { it is PsiWhiteSpace }

        private inline fun isStringLiteralChange(changeSet: TreeChangeEvent): Boolean = isSpecificChange(changeSet) {
            it?.elementType == KtTokens.REGULAR_STRING_PART &&
                    it?.psi?.parentOfTypes(KtAnnotationEntry::class, KtWhenCondition::class) == null
        }

        /**
         * Has to be aligned with [getInsideCodeBlockModificationScope] :
         *
         * result of analysis has to be reflected in dirty scope,
         * the only difference is whitespaces and comments
         */
        fun getInsideCodeBlockModificationDirtyScope(element: PsiElement): PsiElement? {
            if (!element.isPhysical) return null
            // dirty scope for whitespaces and comments is the element itself
            if (element is PsiWhiteSpace || element is PsiComment || element is KDoc) return element

            return getInsideCodeBlockModificationScope(element)?.blockDeclaration
        }

        fun getInsideCodeBlockModificationScope(element: PsiElement): BlockModificationScopeElement? {
            val lambda = element.findTopmostParentOfType<KtLambdaExpression>()
            if (lambda is KtLambdaExpression) {
                lambda.findTopmostParentOfType<KtSuperTypeCallEntry>()?.findTopmostParentOfType<KtClassOrObject>()?.let {
                    return BlockModificationScopeElement(it, it)
                }
            }

            val blockDeclaration = element.findTopmostParentInFile { isBlockDeclaration(it) } as? KtDeclaration ?: return null
            //                KtPsiUtil.getTopmostParentOfType<KtClassOrObject>(element) as? KtDeclaration ?: return null

            // should not be local declaration
            if (KtPsiUtil.isLocal(blockDeclaration))
                return null

            val directParentClassOrObject = PsiTreeUtil.getParentOfType(blockDeclaration, KtClassOrObject::class.java)
            val parentClassOrObject = directParentClassOrObject
                ?.takeIf { !it.isTopLevel() && it.hasModifier(KtTokens.INNER_KEYWORD) }?.let {
                var e: KtClassOrObject? = it
                while (e != null) {
                    e = PsiTreeUtil.getParentOfType(e, KtClassOrObject::class.java)
                    if (e?.hasModifier(KtTokens.INNER_KEYWORD) == false) {
                        break
                    }
                }
                e
            } ?: directParentClassOrObject

            when (blockDeclaration) {
                is KtNamedFunction -> {
                    //                    if (blockDeclaration.visibilityModifierType()?.toVisibility() == Visibilities.PRIVATE) {
                    //                        topClassLikeDeclaration(blockDeclaration)?.let {
                    //                            return BlockModificationScopeElement(it, it)
                    //                        }
                    //                    }
                    if (blockDeclaration.hasBlockBody()) {
                        // case like `fun foo(): String {...<caret>...}`
                        return blockDeclaration.bodyExpression
                            ?.takeIf { it.isAncestor(element) }
                            ?.let {
                                if (parentClassOrObject == directParentClassOrObject) {
                                    BlockModificationScopeElement(blockDeclaration, it)
                                } else if (parentClassOrObject != null) {
                                    BlockModificationScopeElement(parentClassOrObject, it)
                                } else null
                            }
                    } else if (blockDeclaration.hasDeclaredReturnType()) {
                        // case like `fun foo(): String = b<caret>labla`
                        return blockDeclaration.initializer
                            ?.takeIf { it.isAncestor(element) }
                            ?.let {
                                if (parentClassOrObject == directParentClassOrObject) {
                                    BlockModificationScopeElement(blockDeclaration, it)
                                } else if (parentClassOrObject != null) {
                                    BlockModificationScopeElement(parentClassOrObject, it)
                                } else null
                            }
                    }
                }

                is KtProperty -> {
                    //                    if (blockDeclaration.visibilityModifierType()?.toVisibility() == Visibilities.PRIVATE) {
                    //                        topClassLikeDeclaration(blockDeclaration)?.let {
                    //                            return BlockModificationScopeElement(it, it)
                    //                        }
                    //                    }

                    if (blockDeclaration.typeReference != null &&
                        // TODO: it's a workaround for KTIJ-20240 :
                        //  FE does not report CONSTANT_EXPECTED_TYPE_MISMATCH within a property within a class
                        (parentClassOrObject == null || element !is KtConstantExpression)
                    ) {

                        // adding annotations to accessor is the same as change contract of property
                        if (element !is KtAnnotated || element.annotationEntries.isEmpty()) {

                            val properExpression = blockDeclaration.accessors
                                .firstOrNull { (it.initializer ?: it.bodyExpression).isAncestor(element) }
                                ?: blockDeclaration.initializer?.takeIf {
                                    // name references changes in property initializer are OCB, see KT-38443, KT-38762
                                    it.isAncestor(element) && !it.anyDescendantOfType<KtNameReferenceExpression>()
                                }

                            if (properExpression != null) {
                                val declaration =
                                    blockDeclaration.findTopmostParentOfType<KtClassOrObject>() as? KtElement

                                if (declaration != null) {
                                    return if (parentClassOrObject == directParentClassOrObject) {
                                        BlockModificationScopeElement(declaration, properExpression)
                                    } else if (parentClassOrObject != null) {
                                        BlockModificationScopeElement(parentClassOrObject, properExpression)
                                    } else null
                                }
                            }
                        }
                    }
                }

                is KtScriptInitializer -> {
                    return (blockDeclaration.body as? KtCallExpression)
                        ?.lambdaArguments
                        ?.lastOrNull()
                        ?.getLambdaExpression()
                        ?.takeIf { it.isAncestor(element) }
                        ?.let { BlockModificationScopeElement(blockDeclaration, it) }
                }

                is KtClassInitializer -> {
                    blockDeclaration
                        .takeIf { it.isAncestor(element) }
                        ?.let { ktClassInitializer ->
                            parentClassOrObject?.let {
                                return if (parentClassOrObject == directParentClassOrObject) {
                                    BlockModificationScopeElement(it, ktClassInitializer)
                                } else {
                                    BlockModificationScopeElement(parentClassOrObject, ktClassInitializer)
                                }
                            }
                        }
                }

                is KtSecondaryConstructor -> {
                    blockDeclaration.takeIf {
                        it.bodyExpression?.isAncestor(element) ?: false || it.getDelegationCallOrNull()?.isAncestor(element) ?: false
                    }?.let { ktConstructor ->
                        parentClassOrObject?.let {
                            return if (parentClassOrObject == directParentClassOrObject) {
                                BlockModificationScopeElement(it, ktConstructor)
                            } else {
                                BlockModificationScopeElement(parentClassOrObject, ktConstructor)
                            }
                        }
                    }
                }
                //                is KtClassOrObject -> {
                //                    return when (element) {
                //                        is KtProperty, is KtNamedFunction -> {
                //                            if ((element as? KtModifierListOwner)?.visibilityModifierType()?.toVisibility() == Visibilities.PRIVATE)
                //                                BlockModificationScopeElement(blockDeclaration, blockDeclaration) else null
                //                        }
                //                        else -> null
                //                    }
                //                }

                else -> throw IllegalStateException()
            }

            return null
        }

        data class BlockModificationScopeElement(val blockDeclaration: KtElement, val element: KtElement)

        fun isBlockDeclaration(declaration: PsiElement): Boolean {
            return declaration is KtProperty ||
                    declaration is KtNamedFunction ||
                    declaration is KtClassInitializer ||
                    declaration is KtSecondaryConstructor ||
                    declaration is KtScriptInitializer

        }
    }

    private val listeners: MutableList<PureKotlinOutOfCodeBlockModificationListener> = ContainerUtil.createLockFreeCopyOnWriteList()

    private val outOfCodeBlockModificationTrackerImpl = SimpleModificationTracker()
    val outOfCodeBlockModificationTracker = ModificationTracker { outOfCodeBlockModificationTrackerImpl.modificationCount }

    init {
        val treeAspect: TreeAspect = TreeAspect.getInstance(project)
        val model = PomManager.getModel(project)

        model.addModelListener(
            object : PomModelListener {
                override fun isAspectChangeInteresting(aspect: PomModelAspect): Boolean = aspect == treeAspect

                override fun modelChanged(event: PomModelEvent) {
                    val changeSet = event.getChangeSet(treeAspect) as TreeChangeEvent? ?: return
                    val ktFile = changeSet.rootElement.psi.containingFile as? KtFile ?: return

                    incFileModificationCount(ktFile)

                    val changedElements = changeSet.changedElements

                    // skip change if it contains only virtual/fake change
                    if (changedElements.isNotEmpty()) {
                        // ignore formatting (whitespaces etc)
                        if (isFormattingChange(changeSet) ||
                            isCommentChange(changeSet) ||
                            isStringLiteralChange(changeSet)
                        ) return
                    }

                    val inBlockElements = inBlockModifications(changedElements)
                    val physicalFile = ktFile.isPhysical

                    if (inBlockElements.isEmpty()) {
                        val physical = physicalFile && !isReplLine(ktFile.virtualFile)

                        if (physical) {
                            outOfCodeBlockModificationTrackerImpl.incModificationCount()
                        }

                        ktFile.incOutOfBlockModificationCount()

                        didChangeKotlinCode(ktFile, physical)
                    } else if (physicalFile) {
                        inBlockElements.forEach { it.containingKtFile.addInBlockModifiedItem(it) }
                    }
                }
            },
            this,
        )
    }

    fun addListener(listener: PureKotlinOutOfCodeBlockModificationListener, parentDisposable: Disposable) {
        listeners.add(listener)
        Disposer.register(parentDisposable) { removeModelListener(listener) }
    }

    fun removeModelListener(listener: PureKotlinOutOfCodeBlockModificationListener) {
        listeners.remove(listener)
    }

    private fun didChangeKotlinCode(ktFile: KtFile, physical: Boolean) {
        listeners.forEach {
            it.kotlinFileOutOfCodeBlockChanged(ktFile, physical)
        }
    }

    override fun dispose() = Unit
}

private val PER_FILE_MODIFICATION_TRACKER = Key<SimpleModificationTracker>("FILE_OUT_OF_BLOCK_MODIFICATION_COUNT")

val KtFile.perFileModificationTracker: ModificationTracker
    get() = putUserDataIfAbsent(PER_FILE_MODIFICATION_TRACKER, SimpleModificationTracker())

private val FILE_OUT_OF_BLOCK_MODIFICATION_COUNT = Key<Long>("FILE_OUT_OF_BLOCK_MODIFICATION_COUNT")

val KtFile.outOfBlockModificationCount: Long by NotNullableUserDataProperty(FILE_OUT_OF_BLOCK_MODIFICATION_COUNT, 0)

private fun KtFile.incOutOfBlockModificationCount() {
    clearInBlockModifications()

    val count = getUserData(FILE_OUT_OF_BLOCK_MODIFICATION_COUNT) ?: 0
    putUserData(FILE_OUT_OF_BLOCK_MODIFICATION_COUNT, count + 1)
}

/**
 * inBlockModifications is a collection of block elements those have in-block modifications
 */
private val IN_BLOCK_MODIFICATIONS = Key<MutableCollection<KtElement>>("IN_BLOCK_MODIFICATIONS")
private val FILE_IN_BLOCK_MODIFICATION_COUNT = Key<Long>("FILE_IN_BLOCK_MODIFICATION_COUNT")

val KtFile.inBlockModificationCount: Long by NotNullableUserDataProperty(FILE_IN_BLOCK_MODIFICATION_COUNT, 0)

val KtFile.inBlockModifications: Collection<KtElement>
    get() {
        val collection = getUserData(IN_BLOCK_MODIFICATIONS)
        return collection ?: emptySet()
    }

private fun KtFile.addInBlockModifiedItem(element: KtElement) {
    val collection = putUserDataIfAbsent(IN_BLOCK_MODIFICATIONS, mutableSetOf())
    synchronized(collection) {
        val needToAddBlock = collection.none { it.isAncestor(element, strict = false) }
        if (needToAddBlock) {
            collection.removeIf { element.isAncestor(it, strict = false) }
            collection.add(element)
        }
    }
    val count = getUserData(FILE_IN_BLOCK_MODIFICATION_COUNT) ?: 0
    putUserData(FILE_IN_BLOCK_MODIFICATION_COUNT, count + 1)
}

fun KtFile.clearInBlockModifications() {
    val collection = getUserData(IN_BLOCK_MODIFICATIONS)
    collection?.let {
        synchronized(it) {
            it.clear()
        }
    }
}