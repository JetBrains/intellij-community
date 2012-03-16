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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.components.BaseComponent
import org.picocontainer.PicoContainer
import com.intellij.util.messages.MessageBus
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key

/**
 * 
 * @author Kirill Likhodedov
 */
class GitMockProject implements Project {

  String myProjectDir

  GitMockProject(String projectDir) {
    myProjectDir = projectDir
  }

  @Override
  String getName() {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile getBaseDir() {
    return new GitMockVirtualFile(myProjectDir)
  }

  @Override
  String getBasePath() {
    return myProjectDir
  }

  @Override
  VirtualFile getProjectFile() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getProjectFilePath() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getPresentableUrl() {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile getWorkspaceFile() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getLocationHash() {
    throw new UnsupportedOperationException()
  }

  @Override
  String getLocation() {
    throw new UnsupportedOperationException()
  }

  @Override
  void save() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isOpen() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isInitialized() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isDefault() {
    throw new UnsupportedOperationException()
  }

  @Override
  BaseComponent getComponent(String name) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> T getComponent(Class<T> interfaceClass) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> T getComponent(Class<T> interfaceClass, T defaultImplementationIfAbsent) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean hasComponent(Class interfaceClass) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> T[] getComponents(Class<T> baseClass) {
    throw new UnsupportedOperationException()
  }

  @Override
  PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException()
  }

  @Override
  MessageBus getMessageBus() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isDisposed() {
    false
  }

  @Override
  def <T> T[] getExtensions(ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException()
  }

  @Override
  Condition getDisposed() {
    throw new UnsupportedOperationException()
  }

  @Override
  void dispose() {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> T getUserData(Key<T> key) {
    throw new UnsupportedOperationException()
  }

  @Override
  def <T> void putUserData(Key<T> key, T value) {
    throw new UnsupportedOperationException()
  }
}
