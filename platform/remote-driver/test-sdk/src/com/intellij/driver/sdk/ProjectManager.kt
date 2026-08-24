package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.jdk.RemotePath
import com.intellij.driver.sdk.jdk.remotePath

@Remote("com.intellij.openapi.project.ProjectManager")
interface ProjectManager {
  fun getOpenProjects(): Array<Project>
  fun getDefaultProject(): Project
}

/**
 * Beware when adding bindings here: `TrustedProjects` has THREE one-argument `isProjectTrusted` overloads
 * (`Project`, `Path`, `LocatedProject` — `platform-impl` `TrustedProjects.kt:17,28,50`). All three pass the invoker's
 * arity filter, only `Invoker.areTypesCompatible` (`Invoker.java:469`) tells them apart, and when no argument type is
 * compatible the invoker falls back to reflection order (`Invoker.java:452`) — that is, to an arbitrary overload. Every
 * argument must therefore be a proxy of the exact declared type. See AT-5090.
 */
@Remote("com.intellij.ide.trustedProjects.TrustedProjects")
interface TrustedProjects {
  fun isProjectTrusted(project: Project): Boolean
  fun isProjectTrusted(path: RemotePath): Boolean
}

fun Driver.getOpenProjects(rdTarget: RdTarget = RdTarget.DEFAULT): List<Project> {
  return service<ProjectManager>(rdTarget).getOpenProjects().toList()
}

fun Driver.getDefaultProject(): Project {
  return service<ProjectManager>().getDefaultProject()
}

fun Driver.singleProject(rdTarget: RdTarget = RdTarget.DEFAULT): Project {
  val project = service<ProjectManager>(rdTarget).getOpenProjects().singleOrNull() ?: service<LightEditService>().getProject()
  if (project == null) {
    throw IllegalStateException("No projects are opened")
  }
  return project
}

fun Driver.isProjectOpened(project: Project? = null): Boolean {
  val projectToCheck = project ?: getOpenProjects().singleOrNull() ?: service<LightEditService>().getProject()
  if (projectToCheck?.isInitialized() == true) {
    val ideFrame = getIdeFrame(projectToCheck)
    return ideFrame?.getComponent()?.isShowing() == true
  }
  return false
}

fun Driver.isProjectTrusted(project: Project = singleProject()): Boolean {
  return utility(TrustedProjects::class).isProjectTrusted(project)
}

/**
 * Reads the platform trust verdict for a workspace *path*, open or not.
 *
 * [isProjectTrusted] takes an open `Project`, so it cannot answer for a path that never opened one. This asks the same
 * production authority the trust gate itself reads after its dialog, so the answer here is exactly the value the
 * platform acted on, not a parallel reimplementation of the trust store.
 */
fun Driver.isPathTrusted(path: String): Boolean {
  return utility(TrustedProjects::class).isProjectTrusted(remotePath(path))
}