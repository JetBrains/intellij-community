// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.lang.ASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.idea.caches.trackers.PureKotlinCodeBlockModificationListener.Companion.getInsideCodeBlockModificationScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

@Service(Service.Level.PROJECT)
class PureKotlinCodeBlockModificationListener(val project: Project) : Disposable {

    private val outOfCodeBlockModificationTrackerImpl = SimpleModificationTracker()
    val outOfCodeBlockModificationTracker: ModificationTracker = outOfCodeBlockModificationTrackerImpl

    companion object {
        fun getInstance(project: Project): PureKotlinCodeBlockModificationListener = project.service()

        private fun isReplLine(file: VirtualFile): Boolean = file.getUserData(KOTLIN_CONSOLE_KEY) == true

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

        private inline fun isCommentChange(changeSet: TreeChangeEvent): Boolean = isSpecificChange(changeSet) { it is PsiComment }

        private inline fun isFormattingChange(changeSet: TreeChangeEvent): Boolean = isSpecificChange(changeSet) { it is PsiWhiteSpace }

        /**
         * Has to be aligned with [getInsideCodeBlockModificationScope] :
         *
         * result of analysis has to be reflected in dirty scope,
         * the only difference is whitespaces and comments
         */
        fun getInsideCodeBlockModificationDirtyScope(element: PsiElement): PsiElement? {
            if (!element.isPhysical) return null
            // dirty scope for whitespaces and comments is the element itself
            if (element is PsiWhiteSpace || element is PsiComment) return element

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

    init {
        val treeAspect: TreeAspect = TreeAspect.getInstance(project)
        val model = PomManager.getModel(project)

        model.addModelListener(
            object : PomModelListener {
                override fun isAspectChangeInteresting(aspect: PomModelAspect): Boolean = aspect == treeAspect

                override fun modelChanged(event: PomModelEvent) {
                    val changeSet = event.getChangeSet(treeAspect) as TreeChangeEvent? ?: return
                    val ktFile = changeSet.rootElement.psi.containingFile as? KtFile ?: return

                    val changedElements = changeSet.changedElements

                    // skip change if it contains only virtual/fake change
                    if (changedElements.isNotEmpty()) {
                        // ignore formatting (whitespaces etc)
                        if (isFormattingChange(changeSet) || isCommentChange(changeSet)) return
                    }

                    val inBlockElements = inBlockModifications(changedElements)
                    val physicalFile = ktFile.isPhysical

                    if (inBlockElements.isEmpty()) {
                        val physical = physicalFile && (ktFile.virtualFile?.let { !isReplLine(it) } ?: false)
                        ktFile.incOutOfBlockModificationCount()

                        didChangeKotlinCode(ktFile, physical)
                    } else if (physicalFile) {
                        inBlockElements.forEach { it.containingKtFile.addInBlockModifiedItem(it) }
                    }
                }
            },
            this,
        )
        project.messageBus.connect(this).subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
            override fun fileWithNoDocumentChanged(file: VirtualFile) {
                //no document means no pomModel change
                //if psi was not loaded, then the count would be modified by PsiTreeChangeEvent.PROP_UNLOADED_PSI
                //if psi was loaded, then [FileManagerImpl.reloadPsiAfterTextChange] is fired which doesn't provide any explicit change anyway,
                //so one need to inc the tracker to ensure that nothing significant was changed externally
                KotlinCodeBlockModificationListener.getInstance(project).incModificationCount()
            }
        })
    }

    private fun didChangeKotlinCode(ktFile: KtFile, physical: Boolean) {
        if (physical) {
            outOfCodeBlockModificationTrackerImpl.incModificationCount()
            KotlinCodeBlockModificationListener.getInstance(project).incModificationCount()
            KotlinModuleOutOfCodeBlockModificationTracker.getUpdaterInstance(project).onKotlinPhysicalFileOutOfBlockChange(ktFile, true)
        }
    }

    override fun dispose() = Unit
}

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
        val collection = getUserData(IN_BLOCK_MODIFICATIONS) ?: return emptyList()
        return synchronized(collection) {
            if (collection.isNotEmpty()) {
                ArrayList(collection)
            } else {
                emptyList()
            }
        }
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

fun KtFile.removeInBlockModifications(blockModifications: Collection<KtElement>) {
    if (blockModifications.isEmpty()) return

    getUserData(IN_BLOCK_MODIFICATIONS)?.let { collection ->
        synchronized(collection) {
            collection.removeAll(blockModifications.toSet())
        }
    }
}

fun KtFile.clearInBlockModifications() {
    getUserData(IN_BLOCK_MODIFICATIONS)?.let { collection ->
        synchronized(collection) {
            collection.clear()
        }
    }
}