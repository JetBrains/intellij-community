/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.statistics.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.reference.SoftReference;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ScrambledInputStream;
import com.intellij.util.ScrambledOutputStream;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;

public class StatisticsManagerImpl extends StatisticsManager {
  private static final int UNIT_COUNT = 997;
  private static final Object LOCK = new Object();

  @NonNls private static final String STORE_PATH = PathManager.getSystemPath() + File.separator + "stat";

  private final SoftReference[] myUnits = new SoftReference[UNIT_COUNT];
  private final HashSet<StatisticsUnit> myModifiedUnits = new HashSet<>();
  private boolean myTestingStatistics;

  public int getUseCount(@NotNull final StatisticsInfo info) {
    if (info == StatisticsInfo.EMPTY) return 0;

    int useCount = 0;

    for (StatisticsInfo conjunct : info.getConjuncts()) {
      useCount = Math.max(doGetUseCount(conjunct), useCount);
    }

    return useCount;
  }

  private int doGetUseCount(StatisticsInfo info) {
    String key1 = info.getContext();
    int unitNumber = getUnitNumber(key1);
    synchronized (LOCK) {
      StatisticsUnit unit = getUnit(unitNumber);
      return unit.getData(key1, info.getValue());
    }
  }

  @Override
  public int getLastUseRecency(@NotNull StatisticsInfo info) {
    if (info == StatisticsInfo.EMPTY) return 0;

    int recency = Integer.MAX_VALUE;
    for (StatisticsInfo conjunct : info.getConjuncts()) {
      recency = Math.min(doGetRecency(conjunct), recency);
    }
    return recency;
  }

  private int doGetRecency(StatisticsInfo info) {
    String key1 = info.getContext();
    int unitNumber = getUnitNumber(key1);
    synchronized (LOCK) {
      StatisticsUnit unit = getUnit(unitNumber);
      return unit.getRecency(key1, info.getValue());
    }
  }

  public void incUseCount(@NotNull final StatisticsInfo info) {
    if (info == StatisticsInfo.EMPTY) return;
    if (ApplicationManager.getApplication().isUnitTestMode() && !myTestingStatistics) {
      return;
    }

    ApplicationManager.getApplication().assertIsDispatchThread();

    for (StatisticsInfo conjunct : info.getConjuncts()) {
      doIncUseCount(conjunct);
    }
  }

  private void doIncUseCount(StatisticsInfo info) {
    final String key1 = info.getContext();
    int unitNumber = getUnitNumber(key1);
    synchronized (LOCK) {
      StatisticsUnit unit = getUnit(unitNumber);
      unit.incData(key1, info.getValue());
      myModifiedUnits.add(unit);
    }
  }

  public StatisticsInfo[] getAllValues(final String context) {
    final String[] strings;
    synchronized (LOCK) {
      strings = getUnit(getUnitNumber(context)).getKeys2(context);
    }
    return ContainerUtil.map2Array(strings, StatisticsInfo.class, (NotNullFunction<String, StatisticsInfo>)s -> new StatisticsInfo(context, s));
  }

  public void save() {
    synchronized (LOCK) {
      if (!ApplicationManager.getApplication().isUnitTestMode()){
        ApplicationManager.getApplication().assertIsDispatchThread();
        for (StatisticsUnit unit : myModifiedUnits) {
          saveUnit(unit.getNumber());
        }
      }
      myModifiedUnits.clear();
    }
  }

  private StatisticsUnit getUnit(int unitNumber) {
    SoftReference ref = myUnits[unitNumber];
    StatisticsUnit unit = (StatisticsUnit)SoftReference.dereference(ref);
    if (unit != null) return unit;
    unit = loadUnit(unitNumber);
    if (unit == null){
      unit = new StatisticsUnit(unitNumber);
    }
    myUnits[unitNumber] = new SoftReference<>(unit);
    return unit;
  }

  private static StatisticsUnit loadUnit(int unitNumber) {
    StatisticsUnit unit = new StatisticsUnit(unitNumber);
    if (!ApplicationManager.getApplication().isUnitTestMode()){
      String path = getPathToUnit(unitNumber);
      try{
        InputStream in = new BufferedInputStream(new FileInputStream(path));
        in = new ScrambledInputStream(in);
        try{
          unit.read(in);
        }
        finally{
          in.close();
        }
      }
      catch(IOException e){
      }
      catch(WrongFormatException e){
      }
    }
    return unit;
  }

  private void saveUnit(int unitNumber){
    if (!createStoreFolder()) return;
    StatisticsUnit unit = getUnit(unitNumber);
    String path = getPathToUnit(unitNumber);
    try{
      OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
      out = new ScrambledOutputStream(out);
      try {
        unit.write(out);
      }
      finally{
        out.close();
      }
    }
    catch(IOException e){
      Messages.showMessageDialog(
        IdeBundle.message("error.saving.statistics", e.getLocalizedMessage()),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  private static int getUnitNumber(String key1) {
    return Math.abs(key1.hashCode()) % UNIT_COUNT;
  }

  private static boolean createStoreFolder(){
    File homeFile = new File(STORE_PATH);
    if (!homeFile.exists()){
      if (!homeFile.mkdirs()){
        Messages.showMessageDialog(
          IdeBundle.message("error.saving.statistic.failed.to.create.folder", STORE_PATH),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getPathToUnit(int unitNumber) {
    return STORE_PATH + File.separator + "unit." + unitNumber;
  }

  @TestOnly
  public void enableStatistics(@NotNull Disposable parentDisposable) {
    myTestingStatistics = true;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        synchronized (LOCK) {
          Arrays.fill(myUnits, null);
        }
        myTestingStatistics = false;
      }
    });
  }

}