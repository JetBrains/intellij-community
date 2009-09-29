package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;


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

  @Nullable
  public abstract String getScrambledPasswordForCvsRoot(String cvsroot);

  public abstract CvsLoginWorker getLoginWorker(final ModalityContext executor, final Project project, final PServerCvsSettings pServerCvsSettings);
}
