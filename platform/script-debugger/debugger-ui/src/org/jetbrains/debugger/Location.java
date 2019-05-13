/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.debugger;

import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * We use term "location" instead of "position" because webkit uses term "location"
 */
public final class Location {
  private final Url url;
  private final Script script;

  private final int line;
  private final int column;

  public Location(@NotNull Url url, int line, int column) {
    this.url = url;
    this.line = line;
    this.column = column;
    script = null;
  }

  public Location(@NotNull Script script, int line, int column) {
    this.url = script.getUrl();
    this.line = line;
    this.column = column;
    this.script = script;
  }

  public Location(@NotNull Url url, int line) {
    this(url, line, -1);
  }

  @NotNull
  public Location withoutColumn() {
    return script == null ? new Location(url, line) : new Location(script, line, -1);
  }

  @NotNull
  public Url getUrl() {
    return url;
  }

  @Nullable
  public Script getScript() {
    return script;
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Location location = (Location)o;
    return column == location.column && line == location.line && url.equals(location.url);
  }

  @Override
  public int hashCode() {
    int result = url.hashCode();
    result = 31 * result + line;
    result = 31 * result + column;
    return result;
  }
}