// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware

// Allow searching java classes in jars in script dependencies, this is needed for stuff like completion and autoimport
class JavaClassesInScriptDependenciesShortNameCache(private val project: Project) : PsiShortNamesCache() {
    override fun getAllClassNames() = emptyArray<String>()

    override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<out PsiClass> {
        val classpathScope = ScriptDependencyAware.getInstance(project).getAllScriptsDependenciesClassFilesScope()
        val classes = StubIndex.getElements(
          JavaShortClassNameIndex.getInstance().key, name, project, classpathScope.intersectWith(scope), PsiClass::class.java
        )
        return classes.toTypedArray()
    }

    override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

    override fun getAllMethodNames() = emptyArray<String>()

    override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> = PsiField.EMPTY_ARRAY

    override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

    override fun processMethodsWithName(
      name: String,
      scope: GlobalSearchScope,
      processor: Processor<in PsiMethod>
    ) = true

    override fun getAllFieldNames() = emptyArray<String>()

    override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> = PsiField.EMPTY_ARRAY
}