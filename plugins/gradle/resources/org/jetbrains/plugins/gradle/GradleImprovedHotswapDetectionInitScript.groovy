// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionAdapter
import org.gradle.api.tasks.TaskState

import java.security.MessageDigest

import static groovy.io.FileType.FILES

/**
 * This init script generates a file containing a list of task output
 * files that have been modified during the gradle run.
 *
 * This script hooks into Gradle task execution lifecycle. For a very
 * similar script that uses Gradle Build Services introduced in Gradle 6.8,
 * see GradleImprovedHotswapDetectionInitScriptUsingService.groovy
 *
 * The files are determined to be modified by hashing output files
 * before and after gradle execution.
 *
 * We use SHA-256 to hash each file, and folder recursively
 *
 * If the hash is different, or the file did not exist before task was
 * executed, we write it to "initScriptOutputFile" file (which is set
 * from inside
 * {@link org.jetbrains.plugins.gradle.execution.build.GradleImprovedHotswapDetection}
 * class
 *
 * For each updated class we write a two lines to the output file in the format
 *   root:<root>
 *   path:<relative class path>
 * e.g.
 *   root:/repo/project/build/classes/java/main/
 *   path:com/acme/MyClass.class
 *
 * Then, in
 * {@link org.jetbrains.plugins.gradle.execution.build.GradleImprovedHotswapOutput}
 * we read the output file and parse it
 * Finally, {@link in org.jetbrains.plugins.gradle.execution.build.GradleImprovedHotswapDetection}
 * we call {@link com.intellij.task.ProjectTaskContext#fileGenerated} for each generated file
 *
 * This fileGenerated call triggers Java hotswap
 */

Map<Task,Map<File,String>> beforeTaskFileHashes = [:]
def effectiveTasks = []

def outputFile = new File("%s")

gradle.taskGraph.addTaskExecutionListener(new TaskExecutionAdapter() {
  void beforeExecute(Task task) {
    if (task.outputs.hasOutput) {
      beforeTaskFileHashes.put(task, GradleImprovedHotswapDetectionUtils.hashTaskOutputs(task.outputs.files.files))
    }
  }

  void afterExecute(Task task, TaskState state) {
    if ((state.didWork || (state.skipped && state.skipMessage == 'FROM-CACHE')) && task.outputs.hasOutput) {
      effectiveTasks.add(task)
    }
  }
})

gradle.addBuildListener(new BuildAdapter() {
  void buildFinished(BuildResult result) {
    effectiveTasks.each { Task task ->
      GradleImprovedHotswapDetectionUtils.detectModifiedTaskOutputs(task, outputFile, beforeTaskFileHashes)
    }
  }
})
