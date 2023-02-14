// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

import java.security.MessageDigest

import static groovy.io.FileType.FILES

/**
 * This init script generates a file containing a list of task output
 * files that have been modified during the gradle run.
 *
 * This script is very similar to GradleImprovedHotswapDetectionInitScript.groovy
 * The only difference is that it uses Gradle "build service" feature introduced in
 * Grade 6.8 (see https://docs.gradle.org/current/userguide/build_services.html)
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

def outputFile = new File("%s")

abstract class OutputPathCollectorService
  implements BuildService<Params>, AutoCloseable {

  interface Params extends BuildServiceParameters {
    Property<File> getOutputFile()
  }

  // Note that this script will hash *all* the output files for *all* executed tasks TWICE
  //  - once before the task, and once after
  // One possible optimization is to store the before hashes elsewhere, and only hash the output files ONCE
  Map<Task,Map<File,String>> beforeTaskFileHashes = [:]
  Set<Task> tasks = new HashSet<Task>()

  void registerTask(Task task) {
    tasks.add(task)
    if (task.outputs.hasOutput) {
      beforeTaskFileHashes.put(task, GradleImprovedHotswapDetectionUtils.hashTaskOutputs(task.outputs.files.files))
    }
  }

  // this runs after *all* tasks have executed
  @Override
  void close() throws Exception {
    def outputFile = getParameters().outputFile.get()
    tasks.each { Task task ->
      GradleImprovedHotswapDetectionUtils.detectModifiedTaskOutputs(task, outputFile, beforeTaskFileHashes)
    }
  }
}

Provider<OutputPathCollectorService> provider = gradle.sharedServices.registerIfAbsent("outputPathCollectorService",
                                                                                       OutputPathCollectorService) { it.parameters.outputFile.set(outputFile)  }

gradle.taskGraph.whenReady { TaskExecutionGraph tg ->
  tg.allTasks.each { Task t ->
    t.onlyIf {
      // runs before task executes
      provider.get().registerTask(t)
      return true
    }
  }
}
