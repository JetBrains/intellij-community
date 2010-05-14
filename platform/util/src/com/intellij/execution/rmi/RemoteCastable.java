package com.intellij.execution.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Gregory.Shrago
 */
public interface RemoteCastable extends Remote {
  String getCastToClassName() throws RemoteException;
}
