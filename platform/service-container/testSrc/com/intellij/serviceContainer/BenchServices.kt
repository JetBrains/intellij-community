// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.serviceContainer

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

// 100 distinct service classes cycling through the three supported constructor shapes:
//   i % 3 == 0 -> no-arg
//   i % 3 == 1 -> (Project)
//   i % 3 == 2 -> (Project, CoroutineScope)
// Consumed by DoInstantiateClassBenchmarkTest. Kept as real, separately compiled classes (not synthesized at
// runtime) so both instantiation strategies resolve constructors exactly as they do in production.

class BenchService000
class BenchService001(val project: Project)
class BenchService002(val project: Project, val scope: CoroutineScope)
class BenchService003
class BenchService004(val project: Project)
class BenchService005(val project: Project, val scope: CoroutineScope)
class BenchService006
class BenchService007(val project: Project)
class BenchService008(val project: Project, val scope: CoroutineScope)
class BenchService009
class BenchService010(val project: Project)
class BenchService011(val project: Project, val scope: CoroutineScope)
class BenchService012
class BenchService013(val project: Project)
class BenchService014(val project: Project, val scope: CoroutineScope)
class BenchService015
class BenchService016(val project: Project)
class BenchService017(val project: Project, val scope: CoroutineScope)
class BenchService018
class BenchService019(val project: Project)
class BenchService020(val project: Project, val scope: CoroutineScope)
class BenchService021
class BenchService022(val project: Project)
class BenchService023(val project: Project, val scope: CoroutineScope)
class BenchService024
class BenchService025(val project: Project)
class BenchService026(val project: Project, val scope: CoroutineScope)
class BenchService027
class BenchService028(val project: Project)
class BenchService029(val project: Project, val scope: CoroutineScope)
class BenchService030
class BenchService031(val project: Project)
class BenchService032(val project: Project, val scope: CoroutineScope)
class BenchService033
class BenchService034(val project: Project)
class BenchService035(val project: Project, val scope: CoroutineScope)
class BenchService036
class BenchService037(val project: Project)
class BenchService038(val project: Project, val scope: CoroutineScope)
class BenchService039
class BenchService040(val project: Project)
class BenchService041(val project: Project, val scope: CoroutineScope)
class BenchService042
class BenchService043(val project: Project)
class BenchService044(val project: Project, val scope: CoroutineScope)
class BenchService045
class BenchService046(val project: Project)
class BenchService047(val project: Project, val scope: CoroutineScope)
class BenchService048
class BenchService049(val project: Project)
class BenchService050(val project: Project, val scope: CoroutineScope)
class BenchService051
class BenchService052(val project: Project)
class BenchService053(val project: Project, val scope: CoroutineScope)
class BenchService054
class BenchService055(val project: Project)
class BenchService056(val project: Project, val scope: CoroutineScope)
class BenchService057
class BenchService058(val project: Project)
class BenchService059(val project: Project, val scope: CoroutineScope)
class BenchService060
class BenchService061(val project: Project)
class BenchService062(val project: Project, val scope: CoroutineScope)
class BenchService063
class BenchService064(val project: Project)
class BenchService065(val project: Project, val scope: CoroutineScope)
class BenchService066
class BenchService067(val project: Project)
class BenchService068(val project: Project, val scope: CoroutineScope)
class BenchService069
class BenchService070(val project: Project)
class BenchService071(val project: Project, val scope: CoroutineScope)
class BenchService072
class BenchService073(val project: Project)
class BenchService074(val project: Project, val scope: CoroutineScope)
class BenchService075
class BenchService076(val project: Project)
class BenchService077(val project: Project, val scope: CoroutineScope)
class BenchService078
class BenchService079(val project: Project)
class BenchService080(val project: Project, val scope: CoroutineScope)
class BenchService081
class BenchService082(val project: Project)
class BenchService083(val project: Project, val scope: CoroutineScope)
class BenchService084
class BenchService085(val project: Project)
class BenchService086(val project: Project, val scope: CoroutineScope)
class BenchService087
class BenchService088(val project: Project)
class BenchService089(val project: Project, val scope: CoroutineScope)
class BenchService090
class BenchService091(val project: Project)
class BenchService092(val project: Project, val scope: CoroutineScope)
class BenchService093
class BenchService094(val project: Project)
class BenchService095(val project: Project, val scope: CoroutineScope)
class BenchService096
class BenchService097(val project: Project)
class BenchService098(val project: Project, val scope: CoroutineScope)
class BenchService099

internal val benchServiceClasses: List<Class<*>> = listOf(
  BenchService000::class.java,
  BenchService001::class.java,
  BenchService002::class.java,
  BenchService003::class.java,
  BenchService004::class.java,
  BenchService005::class.java,
  BenchService006::class.java,
  BenchService007::class.java,
  BenchService008::class.java,
  BenchService009::class.java,
  BenchService010::class.java,
  BenchService011::class.java,
  BenchService012::class.java,
  BenchService013::class.java,
  BenchService014::class.java,
  BenchService015::class.java,
  BenchService016::class.java,
  BenchService017::class.java,
  BenchService018::class.java,
  BenchService019::class.java,
  BenchService020::class.java,
  BenchService021::class.java,
  BenchService022::class.java,
  BenchService023::class.java,
  BenchService024::class.java,
  BenchService025::class.java,
  BenchService026::class.java,
  BenchService027::class.java,
  BenchService028::class.java,
  BenchService029::class.java,
  BenchService030::class.java,
  BenchService031::class.java,
  BenchService032::class.java,
  BenchService033::class.java,
  BenchService034::class.java,
  BenchService035::class.java,
  BenchService036::class.java,
  BenchService037::class.java,
  BenchService038::class.java,
  BenchService039::class.java,
  BenchService040::class.java,
  BenchService041::class.java,
  BenchService042::class.java,
  BenchService043::class.java,
  BenchService044::class.java,
  BenchService045::class.java,
  BenchService046::class.java,
  BenchService047::class.java,
  BenchService048::class.java,
  BenchService049::class.java,
  BenchService050::class.java,
  BenchService051::class.java,
  BenchService052::class.java,
  BenchService053::class.java,
  BenchService054::class.java,
  BenchService055::class.java,
  BenchService056::class.java,
  BenchService057::class.java,
  BenchService058::class.java,
  BenchService059::class.java,
  BenchService060::class.java,
  BenchService061::class.java,
  BenchService062::class.java,
  BenchService063::class.java,
  BenchService064::class.java,
  BenchService065::class.java,
  BenchService066::class.java,
  BenchService067::class.java,
  BenchService068::class.java,
  BenchService069::class.java,
  BenchService070::class.java,
  BenchService071::class.java,
  BenchService072::class.java,
  BenchService073::class.java,
  BenchService074::class.java,
  BenchService075::class.java,
  BenchService076::class.java,
  BenchService077::class.java,
  BenchService078::class.java,
  BenchService079::class.java,
  BenchService080::class.java,
  BenchService081::class.java,
  BenchService082::class.java,
  BenchService083::class.java,
  BenchService084::class.java,
  BenchService085::class.java,
  BenchService086::class.java,
  BenchService087::class.java,
  BenchService088::class.java,
  BenchService089::class.java,
  BenchService090::class.java,
  BenchService091::class.java,
  BenchService092::class.java,
  BenchService093::class.java,
  BenchService094::class.java,
  BenchService095::class.java,
  BenchService096::class.java,
  BenchService097::class.java,
  BenchService098::class.java,
  BenchService099::class.java,
)
