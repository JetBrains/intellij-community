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
package hg4idea.test;

import com.intellij.dvcs.test.MockVirtualFile;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.impl.VcsEnvironmentsProxyCreator;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgPlatformFacade;
import org.zmlx.hg4idea.HgVcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgMockVcsManager extends ProjectLevelVcsManager {

  Project myProject;
  HgPlatformFacade myPlatformFacade;
  Collection<String> myRoots = new ArrayList<String>();
  boolean myProjectRootMapping = false;

  HgMockVcsManager(Project project, HgPlatformFacade facade) {
    myProject = project;
    myPlatformFacade = facade;
  }

  public void addRoots(String... roots) {
    myRoots.addAll(Arrays.asList(roots));
  }

  public void setProjectRootMapping() {
    myProjectRootMapping = true;
  }

  @Override
  public VirtualFile[] getRootsUnderVcs(AbstractVcs vcs) {

    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    for (String root : myRoots) {
      roots.add(new MockVirtualFile(root));
    }
    roots.addAll(Arrays.asList(myPlatformFacade.getProjectRootManager(myProject).getContentRoots()));
    return roots.toArray(new VirtualFile[0]);
  }

  @Override
  public List<VcsDirectoryMapping> getDirectoryMappings() {
    List<VcsDirectoryMapping> roots = new ArrayList<VcsDirectoryMapping>();
    for (String root : myRoots) {
      roots.add(new VcsDirectoryMapping(root, HgVcs.VCS_NAME));
    }

    if (myProjectRootMapping) {
      roots.add(new VcsDirectoryMapping("", HgVcs.VCS_NAME));
    }
    return roots;
  }

  @Override
  public List<VcsDirectoryMapping> getDirectoryMappings(AbstractVcs vcs) {
    return getDirectoryMappings();
  }

  @Override
  public void setDirectoryMappings(List<VcsDirectoryMapping> items) {
    for (VcsDirectoryMapping item : items) {
      myRoots.add(item.getDirectory());
    }
  }

  @Override
  public void iterateVfUnderVcsRoot(VirtualFile file, Processor<VirtualFile> processor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsDescriptor[] getAllVcss() {
    throw new UnsupportedOperationException();
  }

  @Override
  public AbstractVcs findVcsByName(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsDescriptor getDescriptor(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AbstractVcs getVcsFor(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AbstractVcs getVcsFor(FilePath file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile getVcsRootFor(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile getVcsRootFor(FilePath file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsRoot getVcsRootObjectFor(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsRoot getVcsRootObjectFor(FilePath file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean checkVcsIsActive(AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean checkVcsIsActive(String vcsName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AbstractVcs[] getAllActiveVcss() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasActiveVcss() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasAnyMappings() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addMessageToConsoleWindow(String message, TextAttributes attributes) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option, @NotNull AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option, AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showProjectOperationInfo(UpdatedFiles updatedFiles, String displayActionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addVcsListener(VcsListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeVcsListener(VcsListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void startBackgroundVcsOperation() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void stopBackgroundVcsOperation() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isBackgroundVcsOperationRunning() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<VirtualFile> getRootsUnderVcsWithoutFiltering(AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<VirtualFile> getDetailedVcsMappings(AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VirtualFile[] getAllVersionedRoots() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VcsRoot[] getAllVcsRoots() {
    List<VcsRoot> vcsRoots = new ArrayList<VcsRoot>();
    List<VirtualFile> repositories = myPlatformFacade.getRepositoryManager(myProject).getRepositories();
    for (VirtualFile repository : repositories) {
      vcsRoots.add(new VcsRoot(getVcsFor(repository), repository));
    }
    return vcsRoots.toArray(new VcsRoot[0]);
  }

  @Override
  public void updateActiveVcss() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsDirectoryMapping getDirectoryMappingFor(FilePath path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDirectoryMapping(String path, String activeVcsName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void iterateVcsRoot(VirtualFile root, Processor<FilePath> iterator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void iterateVcsRoot(VirtualFile root, Processor<FilePath> iterator, com.intellij.openapi.vcs.VirtualFileFilter directoryFilter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AbstractVcs findVersioningVcs(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CheckoutProvider.Listener getCompositeCheckoutListener() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsEventsListenerManager getVcsEventsListenerManager() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected VcsEnvironmentsProxyCreator getProxyCreator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsHistoryCache getVcsHistoryCache() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ContentRevisionCache getContentRevisionCache() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFileInContent(VirtualFile vf) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean dvcsUsedInProject() {
    throw new UnsupportedOperationException();
  }
}
