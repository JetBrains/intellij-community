package com.intellij.javascript.web.webTypes.json;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.ArrayList;
import java.util.Map;

public interface GenericContributionsHost {

  @JsonAnyGetter
  public Map<String, ? extends ArrayList<? extends GenericContributionOrProperty>> getAdditionalProperties();

}
