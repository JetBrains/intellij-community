// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans.legacy;

import java.util.List;

public class FSLegacySession {
  public String id;
  public String build;
  public List<FSLegacyGroup> groups = null;
}
