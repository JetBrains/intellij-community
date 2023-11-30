// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.*;

public interface MessageBuilder {

  @CheckReturnValue
  @NotNull MessageBuilder withTitle(String title);

  @CheckReturnValue
  @NotNull MessageBuilder withText(String text);

  @CheckReturnValue
  @NotNull MessageBuilder withKind(Message.Kind kind);

  @CheckReturnValue
  @NotNull MessageBuilder withGroup(String group);

  @CheckReturnValue
  @NotNull MessageBuilder withException(Exception e);

  @CheckReturnValue
  @NotNull MessageBuilder withLocation(Message.FilePosition filePosition);

  @CheckReturnValue
  @NotNull MessageBuilder withLocation(String filePath, int line, int column);

  @CheckReturnValue
  @NotNull MessageBuilder withProject(Project project);

  @NotNull Message build();
}
