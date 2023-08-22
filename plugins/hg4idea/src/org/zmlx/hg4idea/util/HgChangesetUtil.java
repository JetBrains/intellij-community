// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.util;

import org.jetbrains.annotations.NonNls;

/**
 * Utilities for operations involving working with a number of changesets: log, incoming, outgoing, parents, etc.
 * Storage for different mercurial response separators.
 *
 * @author Kirill Likhodedov
 */
public final class HgChangesetUtil {

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
  @NonNls
  public static String makeTemplate(@NonNls String... templateItems) {
    StringBuilder template = new StringBuilder();

    for (String item : templateItems) {
      template.append(item).append(ITEM_SEPARATOR);
    }

    template.append(CHANGESET_SEPARATOR);
    return template.toString();
  }
}
