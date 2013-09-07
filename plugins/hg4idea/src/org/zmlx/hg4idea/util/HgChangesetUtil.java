/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.zmlx.hg4idea.util;

import com.intellij.openapi.util.SystemInfo;

/**
 * Utilities for operations involving working with a number of changesets: log, incoming, outgoing, parents, etc.
 * Storage for different mercurial response separators.
 *
 * @author Kirill Likhodedov
 */
public class HgChangesetUtil {

  public static final String CHANGESET_SEPARATOR = "\u0003";
  public static final String ITEM_SEPARATOR = "\u0017";
  public static final String FILE_SEPARATOR = "\u0001";
//FILE_SEPARATOR used for file_copies pair (by default no separator between prev source and next target)

  /**
   * Common method for hg commands which receive templates via --template option.
   *
   * @param templateItems template items like <pre>{rev}</pre>, <pre>{node}</pre>.
   * @return items joined by ITEM_SEPARATOR, ended by CHANGESET_SEPARATOR.
   */
  public static String makeTemplate(String... templateItems) {
    StringBuilder template = new StringBuilder();

    for (String item : templateItems) {
      template.append(item).append(ITEM_SEPARATOR);
    }

    template.append(CHANGESET_SEPARATOR);
    return template.toString();
  }
}
