// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ExternalUploadSendEvent extends ExternalSystemEvent {
  private final int mySucceed;
  private final int myFailed;
  private final int myTotal;
  @NotNull private final List<String> mySuccessfullySentFiles;
  @NotNull private final List<Integer> myErrors;

  public ExternalUploadSendEvent(long timestamp, int succeed, int failed, int total,
                                 @NotNull List<String> successfullySentFiles,
                                 @NotNull List<Integer> errors) {
    super(ExternalSystemEventType.SEND, timestamp);
    mySucceed = succeed;
    myFailed = failed;
    myTotal = total;
    mySuccessfullySentFiles = successfullySentFiles;
    myErrors = errors;
  }

  public int getSucceed() {
    return mySucceed;
  }

  public int getFailed() {
    return myFailed;
  }

  public int getTotal() {
    return myTotal;
  }

  public @NotNull List<String> getSuccessfullySentFiles() {
    return mySuccessfullySentFiles;
  }

  public @NotNull List<Integer> getErrors() {
    return myErrors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExternalUploadSendEvent event = (ExternalUploadSendEvent)o;
    return mySucceed == event.mySucceed &&
           myFailed == event.myFailed &&
           myTotal == event.myTotal &&
           Objects.equals(mySuccessfullySentFiles, event.mySuccessfullySentFiles) &&
           Objects.equals(myErrors, event.myErrors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), mySucceed, myFailed, myTotal, mySuccessfullySentFiles, myErrors);
  }
}
