package org.jetbrains.jps.android;

import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildConfigState implements ValidityState {
  private final String myPackage;
  private final Set<String> myLibPackages;
  private final boolean myDebug;

  public AndroidBuildConfigState(@NotNull String aPackage, @NotNull Collection<String> libPackages, boolean debug) {
    myPackage = aPackage;
    myDebug = debug;
    myLibPackages = new HashSet<String>(libPackages);
  }

  public AndroidBuildConfigState(@NotNull DataInput in) throws IOException {
    myPackage = in.readUTF();

    final int libPackagesCount = in.readInt();
    myLibPackages = new HashSet<String>(libPackagesCount);
    for (int i = 0; i < libPackagesCount; i++) {
      myLibPackages.add(in.readUTF());
    }
    myDebug = in.readBoolean();
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidBuildConfigState)) {
      return false;
    }
    final AndroidBuildConfigState otherState1 = (AndroidBuildConfigState)otherState;
    return otherState1.myDebug == myDebug &&
           otherState1.myPackage.equals(myPackage) &&
           otherState1.myLibPackages.equals(myLibPackages);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myPackage);
    out.writeInt(myLibPackages.size());
    for (String libPackage : myLibPackages) {
      out.writeUTF(libPackage);
    }
    out.writeBoolean(myDebug);
  }
}
