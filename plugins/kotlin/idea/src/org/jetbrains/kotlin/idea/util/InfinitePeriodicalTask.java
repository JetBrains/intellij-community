// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class InfinitePeriodicalTask {
    private final Alarm myUpdateAlarm;
    private final long delay;
    private final Computable<? extends LongRunningReadTask> taskProvider;
    private LongRunningReadTask currentTask;

    public InfinitePeriodicalTask(
            long delay, @NotNull Alarm.ThreadToUse threadToUse, Disposable parentDisposable,
            Computable<? extends LongRunningReadTask> taskProvider
    ) {
        myUpdateAlarm = new Alarm(threadToUse, parentDisposable);
        this.delay = delay;
        this.taskProvider = taskProvider;
    }

    public InfinitePeriodicalTask start() {
        myUpdateAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
                myUpdateAlarm.addRequest(this, delay);
                LongRunningReadTask task = taskProvider.compute();

                boolean invalidRequest = !task.init();

                //noinspection unchecked
                if (task.shouldStart(currentTask)) {
                    currentTask = task;
                    currentTask.run();
                }
                else if (invalidRequest) {
                    currentTask = null;
                }
            }
        }, delay);

        return this;
    }
}
