// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class ConflictFileTypeMappingTrackerTest extends TestCase {
  public void testEveryPossibleFriggingCombination() {
    IdeaPluginDescriptorImpl bundled1 = createDescriptor(PluginId.getId("bundled1"), true);
    IdeaPluginDescriptorImpl bundled2 = createDescriptor(PluginId.getId("bundled2"), true);
    IdeaPluginDescriptorImpl core = createDescriptor(PluginManagerCore.CORE_ID, true);
    IdeaPluginDescriptorImpl user1 = createDescriptor(PluginId.getId("user1"), false);
    IdeaPluginDescriptorImpl user2 = createDescriptor(PluginId.getId("user2"), false);

    assertConflict(bundled1, bundled2, false, false);
    assertConflict(bundled1, core, false, true);
    assertConflict(bundled1, user1, true, true);
    assertConflict(core, bundled1, true, true);
    assertConflict(core, user1, true, true);
    assertConflict(user1, bundled1, false, true);
    assertConflict(user1, core, false, true);
    assertConflict(user1, user2, true, false);
  }

  private static void assertConflict(@NotNull PluginDescriptor oldDescriptor,
                                     @NotNull PluginDescriptor newDescriptor,
                                     boolean expectedResolveToNew,
                                     boolean expectedApprove) {
    FileType oldFileType = createFakeType("old");
    FileType newFileType = createFakeType("new");
    ExtensionFileNameMatcher matcher = new ExtensionFileNameMatcher("wow");
    ConflictingFileTypeMappingTracker.ResolveConflictResult result;
    FileTypeManagerImpl.FileTypeWithDescriptor oldFtd = new FileTypeManagerImpl.FileTypeWithDescriptor(oldFileType, oldDescriptor);
    FileTypeManagerImpl.FileTypeWithDescriptor newFtd = new FileTypeManagerImpl.FileTypeWithDescriptor(newFileType, newDescriptor);
    result = ConflictingFileTypeMappingTracker.resolveConflict(matcher, oldFtd, newFtd);
    assertSame(expectedResolveToNew ? newFileType : oldFileType, result.resolved.fileType);
    assertEquals(expectedApprove, result.approved);
  }

  @NotNull
  private static FileType createFakeType(@NotNull String name) {
    AbstractFileType oldFileType = new AbstractFileType(new SyntaxTable()){
      @Override
      public String toString() {
        return name;
      }
    };
    oldFileType.setName(name);
    return oldFileType;
  }

  @NotNull
  private static IdeaPluginDescriptorImpl createDescriptor(PluginId id, boolean isBundled) {
    IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(Path.of(""), isBundled);
    descriptor.readForTest(new Element("empty"), id);
    return descriptor;
  }
}
