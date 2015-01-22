package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.MatchOptions;
import org.jdom.Element;

/**
 * Configuration of the search
 */
public class SearchConfiguration extends Configuration {
  private MatchOptions matchOptions;

  public SearchConfiguration() {
    matchOptions = new MatchOptions();
  }

  public MatchOptions getMatchOptions() {
    return matchOptions;
  }

  public void setMatchOptions(MatchOptions matchOptions) {
    this.matchOptions = matchOptions;
  }

  public void readExternal(Element element) {
    super.readExternal(element);

    matchOptions.readExternal(element);
  }

  public void writeExternal(Element element) {
    super.writeExternal(element);

    matchOptions.writeExternal(element);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SearchConfiguration)) return false;
    if (!super.equals(o)) return false;
    return matchOptions.equals(((SearchConfiguration)o).matchOptions);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + matchOptions.hashCode();
  }
}
