// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface JBAccountInfoServiceEx extends JBAccountInfoService{
  class RDClientData {
    @Nullable
    public JBAData userData;

    @Nullable
    public String idToken;

    public RDClientData() {
    }

    public RDClientData(@Nullable String userId, @Nullable String loginName, @Nullable String email, @Nullable String idToken) {
      this(userId != null? new JBAData(userId, loginName, email) : null, idToken);
    }

    public RDClientData(@Nullable JBAData userData, @Nullable String idToken) {
      this.userData = userData;
      this.idToken = idToken;
    }

    public String toJson() {
      return new GsonBuilder().create().toJson(this);
    }

    public static @Nullable RDClientData fromJson(String json) {
      try {
        return new GsonBuilder().create().fromJson(json, RDClientData.class);
      }
      catch (Throwable e) {
        return null;
      }
    }

  }

  void update(RDClientData data);

}
