/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class LogicalRootsManagerTest extends LightPlatformTestCase {
  public void test1() {
    final LogicalRootsManager manager = LogicalRootsManager.getLogicalRootsManager(getProject());
                                                
    final List<LogicalRoot> list = manager.getLogicalRoots(getModule());
    assertEquals(1, list.size());

    final LogicalRootType<LogicalRoot> mockType = LogicalRootType.create("mockLogicalRootType");

    manager.registerLogicalRootProvider(mockType, module -> {
      final List<LogicalRoot> result = new ArrayList<>();
      result.add(new LogicalRoot() {
        @Override
        @NotNull
        public VirtualFile getVirtualFile() {
          return getSourceRoot();
        }

        @Override
        @NotNull
        public LogicalRootType getType() {
          return LogicalRootType.SOURCE_ROOT;
        }
      });

      return result;
    });

    final List<LogicalRoot> roots2 = manager.getLogicalRoots(getModule());
    assertEquals(2, roots2.size());

    final List<LogicalRoot> roots3 = manager.getLogicalRootsOfType(getModule(), mockType);
    assertEquals(1, roots3.size());
  }

  public void test2() {
    final LogicalRootsManager manager = LogicalRootsManager.getLogicalRootsManager(getProject());

    final LogicalRootType<LogicalRoot> mockType = LogicalRootType.create("mockLogicalRootType");
    manager.registerRootType(StdFileTypes.XHTML, mockType);

    assertEquals(0, manager.getRootTypes(StdFileTypes.HTML).length);
    assertEquals(1, manager.getRootTypes(StdFileTypes.XHTML).length);
  }
}
 