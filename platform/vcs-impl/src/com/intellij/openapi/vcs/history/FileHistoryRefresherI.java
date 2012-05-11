package com.intellij.openapi.vcs.history;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 4/27/12
 * Time: 11:47 AM
 * To change this template use File | Settings | File Templates.
 */
public interface FileHistoryRefresherI {
  void run(boolean isRefresh);

  boolean isFirstTime();
}
