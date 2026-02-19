package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project


@Remote("com.jetbrains.rd.platform.ProjectUtilKt", plugin = "com.intellij/intellij.rd.platform")
interface RdProjectUtil {
  fun getRdProjectId(project: Project): RdProjectId
}

@Remote("com.jetbrains.rd.ide.model.RdProjectId", plugin = "com.intellij/intellij.rd.ide.model.generated")
interface RdProjectId {
  val value: String
}