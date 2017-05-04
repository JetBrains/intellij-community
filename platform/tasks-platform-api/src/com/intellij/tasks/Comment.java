/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.tasks;

import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public abstract class Comment {

  public static final Comment[] EMPTY_ARRAY = new Comment[0];

  public abstract String getText();

  @Nullable
  public abstract String getAuthor();

  @Nullable
  public abstract Date getDate();

  public void appendTo(StringBuilder builder) {
    builder.append("<hr>");
    if (getAuthor() != null) {
      builder.append("<b>Author:</b> ").append(getAuthor()).append("<br>");
    }
    if (getDate() != null) {
      builder.append("<b>Date:</b> ").append(DateFormatUtil.formatDateTime(getDate())).append("<br>");
    }
    builder.append(getText().replace("\n", "<br>")).append("<br>");
  }
}
