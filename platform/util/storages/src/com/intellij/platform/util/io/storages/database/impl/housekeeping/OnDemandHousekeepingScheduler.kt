// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.housekeeping

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.function.BooleanSupplier

/**
 * Runs round of registered housekeepers on explicit client request;
 * The client selects the calling thread and the time of each round.
 * [com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper] requests about inter-rounds duration are ignored.
 */
@ApiStatus.Internal
class OnDemandHousekeepingScheduler(private val coordinator: DatabaseHousekeepingCoordinator) : DatabaseHousekeepingScheduler {
  /** Avoids >1 rounds at the same time */
  private val housekeepingRoundExecutionLock = Any()

  //@GuardedBy(registrations)
  private val registrations = HashSet<HousekeepingRegistration>()

  override fun register(registration: HousekeepingRegistration) {
    synchronized(registrations) {
      registrations.add(registration)
    }
  }

  override fun unregister(registration: HousekeepingRegistration) {
    synchronized(registrations) {
      registrations.remove(registration)
    }
  }

  /**
   * Runs one round on the calling thread and ignores the logical delays returned by housekeepers.
   *
   * @return true if the round completed without external cancellation
   */
  @Throws(IOException::class)
  fun runHousekeepingRound(cancellationRequested: BooleanSupplier): Boolean = synchronized(housekeepingRoundExecutionLock) {
    val registrationsToRun = synchronized(registrations) { registrations.toList() }
    for (registration in registrationsToRun) {
      if (cancellationRequested.asBoolean) {
        return@synchronized false
      }
      coordinator.runHousekeepingIfOpen(registration, cancellationRequested)
      if (cancellationRequested.asBoolean) {
        return@synchronized false
      }
    }
    true
  }
}
