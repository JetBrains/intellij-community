package com.intellij.structuralsearch.plugin.replace.ui;

import org.jdom.Element;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.MatchOptions;

/**
 * @author Maxim.Mossienko
 * Date: Apr 14, 2004
 * Time: 4:41:37 PM
 */
public class ReplaceConfiguration extends Configuration {
  private final ReplaceOptions options = new ReplaceOptions();
  public static final String REPLACEMENT_VARIABLE_SUFFIX = "$replacement";

  public ReplaceOptions getOptions() {
    return options;
  }

  public MatchOptions getMatchOptions() {
    return options.getMatchOptions();
  }

  public void readExternal(Element element) {
    super.readExternal(element);
    options.readExternal(element);
  }

  public void writeExternal(Element element) {
    super.writeExternal(element);
    options.writeExternal(element);
  }

  public boolean equals(Object configuration) {
    if (this == configuration) return true;
    if (!(configuration instanceof ReplaceConfiguration)) return false;
    if (!super.equals(configuration)) return false;
    return options.equals(((ReplaceConfiguration)configuration).options);
  }

  public int hashCode() {
    return 31 * super.hashCode() + options.hashCode();
  }
}
