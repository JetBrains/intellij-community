/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * author: lesya
 */
public interface IgnoredFilesInfo {

  IgnoredFilesInfo IGNORE_NOTHING = new IgnoredFilesInfo() {
    @Override
    public boolean shouldBeIgnored(String fileName) {
      return false;
    }

    @Override
    public boolean shouldBeIgnored(VirtualFile file) {
      return false;
    }
  };

  IgnoredFilesInfo IGNORE_ALL = new IgnoredFilesInfo() {
    @Override
    public boolean shouldBeIgnored(String fileName) {
      return true;
    }

    @Override
    public boolean shouldBeIgnored(VirtualFile file) {
      return true;
    }
  };

  boolean shouldBeIgnored(String fileName);

  boolean shouldBeIgnored(VirtualFile file);
}
