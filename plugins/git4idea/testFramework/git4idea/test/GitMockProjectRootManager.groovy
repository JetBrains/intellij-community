/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.*

/**
 * 
 * @author Kirill Likhodedov
 */
class GitMockProjectRootManager extends ProjectRootManager {

  Collection<VirtualFile> myContentRoots = new ArrayList<VirtualFile>();

  GitMockProjectRootManager() {

  }

  @Override
  VirtualFile[] getContentRoots() {
    myContentRoots
  }

  @Override
  ProjectFileIndex getFileIndex() {
    throw new UnsupportedOperationException()
  }





  @Override
  OrderEnumerator orderEntries() {
    throw new UnsupportedOperationException()
  }

  @Override
  OrderEnumerator orderEntries(Collection<? extends Module> modules) {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile[] getContentRootsFromAllModules() {
    throw new UnsupportedOperationException()
  }

  @Override
  List<String> getContentRootUrls() {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile[] getContentSourceRoots() {
    throw new UnsupportedOperationException()
  }

  @Override
  Sdk getProjectSdk() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getProjectSdkName() {
    throw new UnsupportedOperationException()
  }

  @Override
  void setProjectSdk(Sdk sdk) {
    throw new UnsupportedOperationException()
  }

  @Override
  void setProjectSdkName(String name) {
    throw new UnsupportedOperationException()
  }

  @Override
  long getModificationCount() {
    throw new UnsupportedOperationException()
  }
}
