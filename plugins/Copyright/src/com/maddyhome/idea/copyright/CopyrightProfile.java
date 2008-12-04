package com.maddyhome.idea.copyright;

import com.intellij.profile.ProfileEx;
import com.maddyhome.idea.copyright.util.EntityUtil;

public class CopyrightProfile extends ProfileEx {
  public static final String DEFAULT_COPYRIGHT_NOTICE =
    EntityUtil.encode("Copyright (c) $today.year, Your Corporation. All Rights Reserved.");

  public String notice = DEFAULT_COPYRIGHT_NOTICE;
  public String keyword = EntityUtil.encode("Copyright");

  //read external
  public CopyrightProfile() {
    super("");
  }

  public CopyrightProfile(String profileName) {
    super(profileName);
  }

  public String getNotice() {
    return notice;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setNotice(String text) {
    notice = text;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
