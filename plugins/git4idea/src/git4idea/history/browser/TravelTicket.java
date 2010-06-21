/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.browser;

import java.util.Date;

public class TravelTicket {
  private final boolean myIsBottomReached;
  private final Date myLatestDate;
  private final SHAHash myLastHash;

  public TravelTicket(boolean isBottomReached, Date latestDate, SHAHash lastHash) {
    myIsBottomReached = isBottomReached;
    myLatestDate = latestDate;
    myLastHash = lastHash;
  }

  public boolean isIsBottomReached() {
    return myIsBottomReached;
  }

  public Date getLatestDate() {
    return myLatestDate;
  }

  public SHAHash getLastHash() {
    return myLastHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TravelTicket that = (TravelTicket)o;

    if (!myLatestDate.equals(that.myLatestDate)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myLatestDate.hashCode();
  }
}
