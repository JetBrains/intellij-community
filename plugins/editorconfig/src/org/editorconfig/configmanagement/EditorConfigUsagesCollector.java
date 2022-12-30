// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.psi.PsiFile;
import org.editorconfig.configmanagement.extended.EditorConfigIntellijNameUtil;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.editorconfig.configmanagement.extended.IntellijPropertyKindMap;
import org.editorconfig.core.EditorConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EditorConfigUsagesCollector extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("editorconfig", 1);

  private static final EventId1<OptionType> EDITOR_CONFIG_USED =
    GROUP.registerEvent("editorconfig.applied", EventFields.Enum("property", OptionType.class));

  private enum OptionType {
    Standard,
    IntelliJ,
    Other
  }

  private EditorConfigUsagesCollector() {
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logEditorConfigUsed(@NotNull PsiFile file, @NotNull List<EditorConfig.OutPair> options) {
    options.forEach(
      option -> EDITOR_CONFIG_USED.log(file.getProject(), getOptionType(option.getKey()))
    );
  }

  private static OptionType getOptionType(@NotNull String optionKey) {
    EditorConfigPropertyKind propertyKind = IntellijPropertyKindMap.getPropertyKind(optionKey);
    if (propertyKind.equals(EditorConfigPropertyKind.EDITOR_CONFIG_STANDARD)) {
      return OptionType.Standard;
    }
    else if (optionKey.startsWith(EditorConfigIntellijNameUtil.IDE_PREFIX)) {
      return OptionType.IntelliJ;
    }
    else {
      return OptionType.Other;
    }
  }
}
