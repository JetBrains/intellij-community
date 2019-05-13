/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pkg;

import java.io.File;

public class TestLocalsNames {
  private static void rename(File file, boolean recursively) {
    if (file.isDirectory()) {
      long start = System.currentTimeMillis();

      File[] files = file.listFiles();
      for (File s : files) {
        File dest = new File(s.getAbsolutePath() + ".tmp");
        assert s.renameTo(dest) : "unable to rename " + s + " to " + dest;
      }

      long elapsed = System.currentTimeMillis() - start;
      System.out.println("took " + elapsed + "ms (" + elapsed / files.length + "ms per dir)");
    }
  }
}
