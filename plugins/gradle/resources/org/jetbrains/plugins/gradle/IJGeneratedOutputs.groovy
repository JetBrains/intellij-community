// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionAdapter
import org.gradle.api.tasks.TaskState

import java.security.MessageDigest

import static groovy.io.FileType.FILES

/**
 * How this script works
 *
 * First, it hooks into Gradle task lifecycle - it listens for both
 * beforeExecute and afterExecute events on each task
 *
 * In the beforeExecute, we look at task outputs, and specifically, all the
 * *.class files in the output folders. For each file, we calculate a
 * SHA-256 hash that is stored in "beforeTaskFileHashes". For each task
 * we find all existing output files and hash them
 *
 * Then, in afterExecute, we again look at all task outputs (this time
 * iterating over each output folder separately). We calculate the new
 * hashes for all files, and compare them to hashes before task executed
 *
 * If the hash is different, or the file did not exist before task was
 * executed, we write it to "initScriptOutputFile" file (which is set
 * from inside
 * {@link org.jetbrains.plugins.gradle.execution.build.GradleProjectTaskRunner}
 * class
 *
 * For each updated class we write a line in the format
 *   <root>[////]<relative class path>
 * e.g.
 *   /repo/project/build/classes/java/main/[////]com/acme/MyClass.class
 *
 * Then, in
 * {@link org.jetbrains.plugins.gradle.execution.build.GradleProjectTaskRunner#getAffectedOutputRoots}
 * we read initScriptOutputFile and call
 * {@link com.intellij.task.ProjectTaskContext#fileGenerated}
 */

Map<Task,Map<File,String>> beforeTaskFileHashes = [:]
def effectiveTasks = []

gradle.taskGraph.addTaskExecutionListener(new TaskExecutionAdapter() {
  void beforeExecute(Task task) {
    if (task.outputs.hasOutput) {
      beforeTaskFileHashes.put(task, hashOutputs(task.outputs.files.files))
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
      def beforeHashes = beforeTaskFileHashes[task]
      task.outputs.files.files.each { File outputDir ->
        if (outputDir.directory) {
          def hashes = hashOutputDir(outputDir)
          hashes.each { file, hash ->
            def beforeHash = beforeHashes[file]
            if (beforeHash == null || beforeHash != hash) {
              def relativePath = outputDir.toPath().relativize(file.toPath())
              logger.info "> Task: ${task.path} generated class '$relativePath' in '${outputDir.path}'"

              // Declared in GradleProjectTaskRunner.java
              initScriptOutputFile.append(outputDir.path + "[////]" + relativePath + '\n')
            }
          }
        }
      }
    }
  }
})

private static Map<File,String> hashOutputs(Set<File> outputs) {
  Map<File,String> hashes = [:]

  outputs.each { outputDir ->
    if (outputDir.isDirectory()) {
      hashFilesInDir(outputDir, hashes)
    }
  }
  return hashes
}

private static Map<File,String> hashOutputDir(File outputDir) {
  Map<File,String> hashes = [:]
  hashFilesInDir(outputDir, hashes)
  return hashes
}

private static hashFilesInDir(File dir, Map<File,String> hashes) {
  dir.eachFileRecurse(FILES) {
    if (it.name.endsWith(".class")) {
      hashes.put(it, hashFile(it))
    }
  }
}

private static String hashFile(File file) {
  // Set your algorithm
  // "MD2","MD5","SHA","SHA-1","SHA-256","SHA-384","SHA-512"
  MessageDigest hash = MessageDigest.getInstance("SHA-256")

  FileInputStream fis = new FileInputStream(file)
  try {
    byte[] fileBuffer = new byte[1024]

    int bytesRead
    while ((bytesRead = fis.read(fileBuffer)) != -1) {
      hash.update(fileBuffer, 0, bytesRead)
    }

    return hash.digest().encodeHex()
  } finally {
    fis.close()
  }
}
