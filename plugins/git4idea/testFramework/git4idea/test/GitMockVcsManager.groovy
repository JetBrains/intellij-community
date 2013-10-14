/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsPlatformFacade
import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener
import com.intellij.openapi.vcs.history.VcsHistoryCache
import com.intellij.openapi.vcs.impl.ContentRevisionCache
import com.intellij.openapi.vcs.impl.VcsDescriptor
import com.intellij.openapi.vcs.impl.VcsEnvironmentsProxyCreator
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import org.jetbrains.annotations.NotNull

/**
 * 
 * @author Kirill Likhodedov
 */
public class GitMockVcsManager extends ProjectLevelVcsManager {

  Project myProject
  DvcsPlatformFacade myPlatformFacade
  Collection<String> myRoots = []
  boolean myProjectRootMapping = false
  AbstractVcs myVcs

  GitMockVcsManager(Project project, DvcsPlatformFacade facade) {
    myProject = project
    myPlatformFacade = facade
    myVcs = facade.getVcs(project)
  }

  // TODO remove and use getting all roots from GitRepositoryManager.
  void addRoots(String... roots) {
    roots.each { myRoots << it }
  }

  void setProjectRootMapping() {
    myProjectRootMapping = true
  }

  @Override
  VirtualFile[] getRootsUnderVcs(AbstractVcs vcs) {
    List<VirtualFile> roots = myRoots.collect { new MockVirtualFile(it) }
    roots.addAll(myPlatformFacade.getProjectRootManager(myProject).getContentRoots())
    roots
  }

  @Override
  List<VcsDirectoryMapping> getDirectoryMappings() {
    List<VcsDirectoryMapping> roots = myRoots.collect { new VcsDirectoryMapping(it, "Git") }
    if (myProjectRootMapping) {
      roots << new VcsDirectoryMapping("", "Git")
    }
    roots
  }

  @Override
  List<VcsDirectoryMapping> getDirectoryMappings(AbstractVcs vcs) {
    return getDirectoryMappings();
  }

  @Override
  void setDirectoryMappings(List<VcsDirectoryMapping> items) {
    myRoots = items.collect { it.directory }
  }

  @Override
  void iterateVfUnderVcsRoot(VirtualFile file, Processor<VirtualFile> processor) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener() {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsDescriptor[] getAllVcss() {
    throw new UnsupportedOperationException()
  }

  @Override
  AbstractVcs findVcsByName(String name) {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsDescriptor getDescriptor(String name) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files) {
    throw new UnsupportedOperationException()
  }

  @Override
  AbstractVcs getVcsFor(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException()
  }

  @Override
  AbstractVcs getVcsFor(FilePath file) {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile getVcsRootFor(VirtualFile file) {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile getVcsRootFor(FilePath file) {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsRoot getVcsRootObjectFor(VirtualFile file) {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsRoot getVcsRootObjectFor(FilePath file) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean checkVcsIsActive(AbstractVcs vcs) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean checkVcsIsActive(String vcsName) {
    throw new UnsupportedOperationException()
  }

  @Override
  AbstractVcs[] getAllActiveVcss() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean hasActiveVcss() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean hasAnyMappings() {
    throw new UnsupportedOperationException()
  }

  @Override
  void addMessageToConsoleWindow(String message, TextAttributes attributes) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option, @NotNull AbstractVcs vcs) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option, AbstractVcs vcs) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    throw new UnsupportedOperationException()
  }

  @Override
  void showProjectOperationInfo(UpdatedFiles updatedFiles, String displayActionName) {
    throw new UnsupportedOperationException()
  }

  @Override
  void addVcsListener(VcsListener listener) {
    throw new UnsupportedOperationException()
  }

  @Override
  void removeVcsListener(VcsListener listener) {
    throw new UnsupportedOperationException()
  }

  @Override
  void startBackgroundVcsOperation() {
    throw new UnsupportedOperationException()
  }

  @Override
  void stopBackgroundVcsOperation() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isBackgroundVcsOperationRunning() {
    throw new UnsupportedOperationException()
  }

  @Override
  List<VirtualFile> getRootsUnderVcsWithoutFiltering(AbstractVcs vcs) {
    throw new UnsupportedOperationException()
  }

  @Override
  List<VirtualFile> getDetailedVcsMappings(AbstractVcs vcs) {
    throw new UnsupportedOperationException()
  }

  @Override
  VirtualFile[] getAllVersionedRoots() {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  VcsRoot[] getAllVcsRoots() {
    myPlatformFacade.getRepositoryManager(myProject).repositories.collect {
      new VcsRoot(myVcs, it.root)
    }
  }

  @Override
  void updateActiveVcss() {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsDirectoryMapping getDirectoryMappingFor(FilePath path) {
    throw new UnsupportedOperationException()
  }

  @Override
  void setDirectoryMapping(String path, String activeVcsName) {
    throw new UnsupportedOperationException()
  }

  @Override
  void iterateVcsRoot(VirtualFile root, Processor<FilePath> iterator) {
    throw new UnsupportedOperationException()
  }

  @Override
  void iterateVcsRoot(VirtualFile root, Processor<FilePath> iterator, com.intellij.openapi.vcs.VirtualFileFilter directoryFilter) {
    throw new UnsupportedOperationException()
  }

  @Override
  AbstractVcs findVersioningVcs(VirtualFile file) {
    throw new UnsupportedOperationException()
  }

  @Override
  CheckoutProvider.Listener getCompositeCheckoutListener() {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsEventsListenerManager getVcsEventsListenerManager() {
    throw new UnsupportedOperationException()
  }

  @Override
  protected VcsEnvironmentsProxyCreator getProxyCreator() {
    throw new UnsupportedOperationException()
  }

  @Override
  VcsHistoryCache getVcsHistoryCache() {
    throw new UnsupportedOperationException()
  }

  @Override
  ContentRevisionCache getContentRevisionCache() {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean isFileInContent(VirtualFile vf) {
    throw new UnsupportedOperationException()
  }

  @Override
  boolean dvcsUsedInProject() {
    throw new UnsupportedOperationException()
  }
}
