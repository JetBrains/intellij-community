// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.idea.IdeaLanguageLevel;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

@ApiStatus.Internal
public final class InternalIdeaLanguageLevel implements IdeaLanguageLevel {
  private final String level;

  public InternalIdeaLanguageLevel(String level) {
    this.level = level;
  }

  public boolean isJDK_1_4() {
    return "JDK_1_4".equals(this.level);
  }

  public boolean isJDK_1_5() {
    return "JDK_1_5".equals(this.level);
  }

  public boolean isJDK_1_6() {
    return "JDK_1_6".equals(this.level);
  }

  public boolean isJDK_1_7() {
    return "JDK_1_7".equals(this.level);
  }

  public boolean isJDK_1_8() {
    return "JDK_1_8".equals(this.level);
  }

  @Override
  public String getLevel() {
    return this.level;
  }

  @Override
  public String toString() {
    return "IdeaLanguageLevel{level='" + this.level + "'}";
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof InternalIdeaLanguageLevel &&
                        Objects.equals(this.level, ((InternalIdeaLanguageLevel)o).level);
  }

  @Override
  public int hashCode() {
    return this.level != null ? this.level.hashCode() : 0;
  }
}
