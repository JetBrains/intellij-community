package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.ui.experts.importToCvs.FileExtension;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
/**
 * author: lesya
 */

public class ImportConfiguration extends AbstractConfiguration implements ApplicationComponent{

  public String VENDOR;
  public String RELEASE_TAG;
  public String LOG_MESSAGE;
  public boolean CHECKOUT_AFTER_IMPORT = true;
  public String KEYWORD_SUBSTITUTION_WRAPPERS = "";
  public boolean MAKE_NEW_FILES_READ_ONLY = false;

  public static ImportConfiguration getInstance(){
    return ServiceManager.getService(ImportConfiguration.class);
  }

  public ImportConfiguration() {
    super("ImportConfiguration");
  }

  public Collection<FileExtension> getExtensions() {
    ArrayList<FileExtension> result = new ArrayList<FileExtension>();
    if (KEYWORD_SUBSTITUTION_WRAPPERS == null || KEYWORD_SUBSTITUTION_WRAPPERS.length() == 0)
      return result;

    String[] wrappers = KEYWORD_SUBSTITUTION_WRAPPERS.split(";");
    for (int i = 0; i < wrappers.length; i++) {
      String wrapper = wrappers[i];
      String[] extAndSubstitution = wrapper.split(" ");
      if (extAndSubstitution.length != 2) continue;
      result.add(new FileExtension(extAndSubstitution[0], extAndSubstitution[1]));
    }
    return result;
  }

  public void setExtensions(List items) {
    StringBuffer buffer = new StringBuffer();
    for (Iterator iterator = items.iterator(); iterator.hasNext();) {
      FileExtension extension = (FileExtension)iterator.next();
      buffer.append(extension.getExtension());
      buffer.append(" ");
      buffer.append(extension.getKeywordSubstitution().getSubstitution().toString());
      buffer.append(";");
    }
    KEYWORD_SUBSTITUTION_WRAPPERS = buffer.toString();
  }

}
