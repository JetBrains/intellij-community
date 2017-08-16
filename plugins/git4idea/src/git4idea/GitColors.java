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
package git4idea;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.vcs.VcsColorsProvider;
import com.intellij.vcs.log.VcsLogColors;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author gregsh
 */
public class GitColors extends VcsColorsProvider {

  public static final ColorKey REFS_HEAD = ColorKey.createColorKey("GIT_REFS_HEAD", VcsLogColors.REFS_HEAD);
  public static final ColorKey REFS_LOCAL_BRANCH = ColorKey.createColorKey("GIT_REFS_BRANCH", VcsLogColors.REFS_BRANCH);
  public static final ColorKey REFS_REMOTE_BRANCH = ColorKey.createColorKey("GIT_REFS_BRANCH_REF", VcsLogColors.REFS_BRANCH_REF);
  public static final ColorKey REFS_TAG = ColorKey.createColorKey("GIT_REFS_TAG", VcsLogColors.REFS_TAG);
  public static final ColorKey REFS_OTHER = ColorKey.createColorKey("GIT_REFS_OTHER", VcsLogColors.REFS_TAG);

  @NotNull
  @Override
  public List<ColorDescriptor> getColorDescriptors() {
    return Arrays.asList(
      new ColorDescriptor("VCS Log//Git//Head", REFS_HEAD, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Git//Local branch", REFS_LOCAL_BRANCH, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Git//Remote branch", REFS_REMOTE_BRANCH, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Git//Tag", REFS_TAG, ColorDescriptor.Kind.FOREGROUND),
      new ColorDescriptor("VCS Log//Git//Other", REFS_OTHER, ColorDescriptor.Kind.FOREGROUND)
    );
  }
}
