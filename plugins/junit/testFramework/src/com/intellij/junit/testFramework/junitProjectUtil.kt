// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.util.PathUtil
import junit.framework.TestCase
import java.io.File

fun ModifiableRootModel.addJUnit3Library() {
  val jar = File(PathUtil.getJarPathForClass(TestCase::class.java))
  PsiTestUtil.addLibrary(this, "junit3", jar.parent, jar.name)
}

fun ModifiableRootModel.addJUnit4Library() {
  val jar = File(PathUtil.getJarPathForClass(org.junit.Test::class.java))
  PsiTestUtil.addLibrary(this, "junit4", jar.parent, jar.name)
}

fun ModifiableRootModel.addHamcrestLibrary() {
  val jar = File(PathUtil.getJarPathForClass(org.hamcrest.MatcherAssert::class.java))
  PsiTestUtil.addLibrary(this, "hamcrest-core", jar.parent, jar.name)
  val libraryJar = File(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("hamcrest").first())
  PsiTestUtil.addLibrary(this, "hamcrest-library", libraryJar.parent, libraryJar.name)
}

fun ModifiableRootModel.addJUnit5Library(version: String = "5.12.0") {
  MavenDependencyUtil.addFromMaven(this, "org.junit.jupiter:junit-jupiter-api:$version")
  MavenDependencyUtil.addFromMaven(this, "org.junit.jupiter:junit-jupiter-params:$version")
}