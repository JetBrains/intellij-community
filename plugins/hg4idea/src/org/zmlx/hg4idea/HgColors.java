/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.ui.JBColor;
import com.intellij.vcs.VcsColorsProvider;
import com.intellij.vcs.log.VcsLogColors;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author gregsh
 */
public class HgColors extends VcsColorsProvider {
  public static final ColorKey REFS_TIP = ColorKey.createColorKey("HG_REFS_TIP", VcsLogColors.REFS_HEAD);
  public static final ColorKey REFS_HEAD = ColorKey.createColorKey("HG_REFS_HEAD", VcsLogColors.REFS_LEAF);
  public static final ColorKey REFS_BRANCH = ColorKey.createColorKey("HG_REFS_BRANCH", VcsLogColors.REFS_BRANCH);
  public static final ColorKey REFS_BOOKMARK = ColorKey.createColorKey("HG_REFS_BOOKMARK", VcsLogColors.REFS_BRANCH_REF);
  public static final ColorKey REFS_TAG = ColorKey.createColorKey("HG_REFS_TAG", VcsLogColors.REFS_TAG);

  public static final ColorKey CLOSED_BRANCH = ColorKey.createColorKey("HG_CLOSED_BRANCH", new JBColor(new Color(0x823139), new Color(0xff5f6f)));
  public static final ColorKey LOCAL_TAG = ColorKey.createColorKey("HG_LOCAL_TAG", new JBColor(new Color(0x009090), new Color(0x00f3f3)));
  public static final ColorKey MQ_TAG = ColorKey.createColorKey("HG_MQ_TAG", new JBColor(new Color(0x002f90), new Color(0x0055ff)));

  @NotNull
  @Override
  public List<ColorDescriptor> getColorDescriptors() {
    return Arrays.asList(
      new ColorDescriptor("VCS Log//Mercurial//Tip", REFS_TIP, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Mercurial//Head", REFS_HEAD, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Mercurial//Branch", REFS_BRANCH, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Mercurial//Bookmark", REFS_BOOKMARK, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Mercurial//Tag", REFS_TAG, ColorDescriptor.Kind.FOREGROUND),

      new ColorDescriptor("VCS Log//Mercurial//Closed branch", CLOSED_BRANCH, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Mercurial//Local tag", LOCAL_TAG, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Mercurial//MQ tag", MQ_TAG, ColorDescriptor.Kind.FOREGROUND)
    );
  }
}
