// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.idea.devkit.DevKitBundle

private const val LIGHT_PROJECT_DESCRIPTOR_FQN = "com.intellij.testFramework.LightProjectDescriptor"
private const val DEFAULT_LIGHT_PROJECT_DESCRIPTOR_FQN = "com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor"

/**
 * Reports `com.intellij.testFramework.LightProjectDescriptor` subclasses (test project descriptors and descriptor
 * builders) that do not override both `equals()` and `hashCode()`.
 *
 * `com.intellij.testFramework.LightPlatformTestCase#doSetup` only reuses the shared light project when the previous
 * descriptor `equals()` the current one. With the default identity `equals()`, every freshly created descriptor instance
 * forces a full project re-setup, which slows tests down.
 */
internal class LightProjectDescriptorEqualsHashCodeInspection : DevKitJvmInspection.ForClass() {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowedIncludingTestSources(holder.file) &&
           DevKitInspectionUtil.isClassAvailable(holder, LIGHT_PROJECT_DESCRIPTOR_FQN)
  }

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (!isTestProjectDescriptor(psiClass)) return
    if (overridesEqualsAndHashCode(psiClass)) return
    if (hasSharedDescriptorInstance(psiClass)) return
    val name = psiClass.name ?: return
    sink.highlight(DevKitBundle.message("inspection.light.project.descriptor.equals.hashcode.message", name))
  }
}

private fun isTestProjectDescriptor(psiClass: PsiClass): Boolean {
  if (psiClass.isInterface || psiClass.isEnum || psiClass.isAnnotationType) return false
  if (psiClass is PsiAnonymousClass) return false
  if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false
  if (!InheritanceUtil.isInheritor(psiClass, LIGHT_PROJECT_DESCRIPTOR_FQN)) return false
  // The platform base classes themselves are not the reuse offenders; only their concrete subclasses are.
  val qualifiedName = psiClass.qualifiedName
  return qualifiedName != LIGHT_PROJECT_DESCRIPTOR_FQN && qualifiedName != DEFAULT_LIGHT_PROJECT_DESCRIPTOR_FQN
}

private fun overridesEqualsAndHashCode(psiClass: PsiClass): Boolean {
  return declaresMethod(psiClass, "equals", parameterCount = 1) &&
         declaresMethod(psiClass, "hashCode", parameterCount = 0)
}

/**
 * `true` when the method is declared anywhere in the hierarchy below [java.lang.Object] (i.e. it is actually overridden,
 * not merely inherited from `Object`). A subclass that inherits an override from an intermediate descriptor is therefore
 * treated as having its own `equals()`/`hashCode()`.
 */
private fun declaresMethod(psiClass: PsiClass, name: String, parameterCount: Int): Boolean {
  return psiClass.findMethodsByName(name, true).any { method ->
    method.parameterList.parametersCount == parameterCount &&
    method.containingClass?.qualifiedName != CommonClassNames.JAVA_LANG_OBJECT
  }
}

/**
 * Guards the common correct idiom where a descriptor publishes a canonical shared instance of itself
 * (e.g. `public static final MyDescriptor INSTANCE = new MyDescriptor();`, or a Kotlin `object`). Such instances are
 * reused by identity, so a missing `equals()`/`hashCode()` does not hurt.
 */
private fun hasSharedDescriptorInstance(psiClass: PsiClass): Boolean {
  return psiClass.fields.any { field ->
    field.hasModifierProperty(PsiModifier.STATIC) &&
    InheritanceUtil.isInheritor(field.type, LIGHT_PROJECT_DESCRIPTOR_FQN)
  }
}
