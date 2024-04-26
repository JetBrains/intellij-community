// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.BooleanEventField;
import com.intellij.internal.statistic.eventLog.events.EnumEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class FileChooserUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("ui.file.chooser", 2);

  private static final EnumEventField<Type> TYPE = EventFields.Enum("type", Type.class);
  private static final BooleanEventField FORCED = EventFields.Boolean("forced");
  private static final BooleanEventField JAR_CONTENTS = EventFields.Boolean("jar_contents");
  private static final BooleanEventField NON_LOCAL_ROOTS = EventFields.Boolean("non_local_roots");
  private static final EnumEventField<Filter> FILTER = EventFields.Enum("filter", Filter.class);
  private static final BooleanEventField NON_LOCAL_FILES = EventFields.Boolean("non_local_files");
  private static final VarargEventId CHOOSER_SHOWN = GROUP.registerVarargEvent(
    "chooser_shown", TYPE, FORCED, JAR_CONTENTS, NON_LOCAL_ROOTS, FILTER, NON_LOCAL_FILES);

  private enum Type {NATIVE, CLASSIC, NEW, OTHER}
  private enum Filter {NONE, TYPE, EXT, OTHER}

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void log(FileChooserDialog chooser, FileChooserDescriptor descriptor, VirtualFile[] files) {
    CHOOSER_SHOWN.log(
      TYPE.with(chooserType(chooser)),
      FORCED.with(descriptor.isForcedToUseIdeaFileChooser()),
      JAR_CONTENTS.with(descriptor.isChooseJarContents()),
      NON_LOCAL_ROOTS.with(ContainerUtil.exists(descriptor.getRoots(), root -> !root.isInLocalFileSystem())),
      FILTER.with(filterType(descriptor.getUserData(FileChooserDescriptor.FILTER_TYPE))),
      NON_LOCAL_FILES.with(ContainerUtil.exists(files, file -> !file.isInLocalFileSystem()))
    );
  }

  private static Type chooserType(FileChooserDialog chooser) {
    return chooser instanceof NativeFileChooserDialogImpl ? Type.NATIVE :
           chooser instanceof NewFileChooserDialogImpl ? Type.NEW :
           chooser instanceof FileChooserDialogImpl ? Type.CLASSIC :
           Type.OTHER;
  }

  private static Filter filterType(@Nullable String filterType) {
    return filterType == null ? Filter.NONE :
           filterType.equals("file-type") ? Filter.TYPE :
           filterType.equals("file-ext") ? Filter.EXT :
           Filter.OTHER;
  }
}
