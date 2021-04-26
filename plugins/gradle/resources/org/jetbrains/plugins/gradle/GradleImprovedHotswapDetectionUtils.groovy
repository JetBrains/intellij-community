// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import org.gradle.api.Task

import java.security.MessageDigest

import static groovy.io.FileType.FILES

class GradleImprovedHotswapDetectionUtils {
  static void detectModifiedTaskOutputs(Task task, File outputFile, Map<Task,Map<File,String>> beforeTaskFileHashes) {
    def state = task.state
    def didWork = state.didWork
    def fromCache = state.skipped && state.skipMessage == 'FROM-CACHE'
    def hasOutput = task.outputs.hasOutput
    if ((didWork || fromCache) && hasOutput) {
      def beforeHashes = beforeTaskFileHashes[task]
      task.outputs.files.files.each { File taskOutput ->
        hashTaskOutput(taskOutput) { file, hash ->
          def beforeHash = beforeHashes[file]
          if (beforeHash == null || beforeHash != hash) {
            if (taskOutput.directory) {
              def relativePath = taskOutput.toPath().relativize(file.toPath())
              outputFile.append("root:${taskOutput.path}\n")
              outputFile.append("path:${relativePath}\n")
            } else {
              // sometimes taskOutput can be a file, such as a jar
              // in this case we treat it as an "output root"
              outputFile.append("root:${taskOutput.path}\n")
              outputFile.append("path:\n")
            }
          }
        }
      }
    }
  }

  static Map<File,String> hashTaskOutputs(Set<File> taskOutputs) {
    Map<File,String> hashes = [:]
    taskOutputs.each { taskOutput ->
      hashTaskOutput(taskOutput, { file, hash -> hashes.put(file, hash) })
    }
    return hashes
  }

  private static void hashTaskOutput(File taskOutput, @ClosureParams(value= FromString.class, options="File,String") Closure closure) {
    if (taskOutput.exists()) {
      if (taskOutput.directory) {
        taskOutput.eachFileRecurse(FILES) {
          hashTaskOutput(it, closure)
        }
      }
      else {
        closure.call(taskOutput, hashFile(taskOutput))
      }
    }
  }

  private static String hashFile(File file) {
    file.withInputStream {
      def digest = MessageDigest.getInstance("SHA-256")
      it.eachByte 4096, { buffer, length ->
        digest.update(buffer, 0, length)
      }
      return digest.digest().encodeHex()
    }
  }
}
