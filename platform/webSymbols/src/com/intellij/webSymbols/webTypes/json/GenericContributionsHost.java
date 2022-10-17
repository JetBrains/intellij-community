// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.ArrayList;
import java.util.Map;

public interface GenericContributionsHost {

  @JsonAnyGetter
  public Map<String, ? extends ArrayList<? extends GenericContributionOrProperty>> getAdditionalProperties();
}
