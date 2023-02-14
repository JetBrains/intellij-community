// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import git4idea.i18n.GitBundle;

public enum GitTextConvMode {
  NONE {
    @Override
    public String toString() {
      return GitBundle.message("git.content.transform.none");
    }
  },
  FILTERS {
    @Override
    public String toString() {
      return GitBundle.message("git.content.transform.filters");
    }
  },
  TEXTCONV {
    @Override
    public String toString() {
      return GitBundle.message("git.content.transform.textconv");
    }
  }
}
