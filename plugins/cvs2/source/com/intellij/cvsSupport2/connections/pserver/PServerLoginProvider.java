package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.connections.pserver.PServerCvsSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;


/**
 * author: lesya
 */
public abstract class PServerLoginProvider {
  private static PServerLoginProvider myInstance = new PServerLoginProviderImpl();

  public static PServerLoginProvider getInstance() {
    return myInstance;
  }

  public static void registerPasswordProvider(PServerLoginProvider passProvider){
    myInstance = passProvider;
  }

  public abstract String getScrambledPasswordForCvsRoot(String cvsroot);

  public abstract boolean login(PServerCvsSettings settings, ModalityContext executor);
}
