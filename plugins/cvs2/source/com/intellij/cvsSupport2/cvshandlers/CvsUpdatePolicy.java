/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;

public class CvsUpdatePolicy {
  public static final String BYNARY_MERGED_ID = "BINARY_MERGED";
  public static final String UNKNOWN_TYPE_ID = "UNKNOWN_TYPE";
  public static final String MODIFIED_REMOVED_FROM_SERVER_ID = "MOD_REMOVED_FROM_SERVER";
  public static final String LOCALLY_REMOVED_MODIFIED_ON_SERVER_ID = "LOCALLY_REMOVED_MODIFIED_ON_SERVER";
  public static final String CREATED_BY_SECOND_PARTY_ID = "CREATED_BY_SECOND_PARTY";

  public static UpdatedFiles createUpdatedFiles() {
    UpdatedFiles result = UpdatedFiles.create();

    fillGroups(result);

    return result;
  }

  public static void fillGroups(UpdatedFiles result) {
    result.registerGroup(new FileGroup("Unknown", "Unknown", false, CvsUpdatePolicy.UNKNOWN_TYPE_ID, false));
    result.registerGroup(
      new FileGroup("Locally modified removed from server", "Locally modified removed from server",false, CvsUpdatePolicy.MODIFIED_REMOVED_FROM_SERVER_ID, false));
    result.registerGroup(
      new FileGroup("Locally removed modified on server", "Locally removed modified on server", false, CvsUpdatePolicy.LOCALLY_REMOVED_MODIFIED_ON_SERVER_ID, false));
    result.registerGroup(new FileGroup("Created by second party", "Created by second party", true, CvsUpdatePolicy.CREATED_BY_SECOND_PARTY_ID, false));
    result.registerGroup(new FileGroup("Binary file has to be merged", "Binary file has to be merged", false, CvsUpdatePolicy.BYNARY_MERGED_ID, false));
  }
}
