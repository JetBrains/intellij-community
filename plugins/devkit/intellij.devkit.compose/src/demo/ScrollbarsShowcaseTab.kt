package com.intellij.devkit.compose.demo

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.modifier.trackComponentActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import java.util.*

@Composable
internal fun ScrollbarsShowcaseTab() {
  Column(
    Modifier.trackComponentActivation(LocalComponent.current).fillMaxSize().padding(16.dp).trackActivation(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
      val textFieldState = rememberTextFieldState(ANDROID_IPSUM)
      TextArea(state = textFieldState, modifier = Modifier.size(300.dp))

      Spacer(Modifier.width(10.dp))

      val scrollState = rememberLazyListState()
      VerticallyScrollableContainer(
        scrollState as ScrollableState,
        Modifier.width(200.dp).border(1.dp, JewelTheme.globalColors.borders.normal),
      ) {
        LazyColumn(state = scrollState, contentPadding = PaddingValues(vertical = 8.dp)) {
          itemsIndexed(LIST_ITEMS) { index, item ->
            Column {
              Text(
                text = item,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp + scrollbarContentSafePadding()),
              )

              if (index < LIST_ITEMS.lastIndex) {
                Box(Modifier.height(8.dp)) {
                  Divider(
                    orientation = Orientation.Horizontal,
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Suppress("SpellCheckingInspection")
private const val ANDROID_IPSUM =
  "Jetpack Compose dolor sit amet, viewBinding consectetur adipiscing elit, sed do eiusmod tempor incididunt" +
  " ut unitTest et dolore magna aliqua. Dependency injection enim ad minim veniam, quis nostrud Dagger-Hilt " +
  "ullamco laboris nisi ut aliquip ex ea Lottie animation consequat. Retrofit irure dolor in reprehenderit in" +
  " AndroidX velit esse cillum dolore eu fugiat nulla pariatur. Gradle sync dolor sit amet, compileSdkVersion" +
  " consectetur adipiscing elit, sed do eiusmod minimSdkVersion tempor incididunt ut labore et dolore magna" +
  " aliqua. Ut enim ad activity_main.xml veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip" +
  " ex ea compileOptions consequat. Duis aute irure dolor in reprehenderit in logcat velit esse cillum dolore" +
  "eu fugiat nulla pariatur. Excepteur sint occaecat proident, sunt in culpa qui officia debugImplementation" +
  " deserunt mollit anim id est laborum. Manifest merger dolor sit amet, androidx.appcompat.app.AppCompatAct" +
  " adipiscing elit, sed do eiusmod tempor incididunt ut buildToolsVersion et dolore magna aliqua. Proguard" +
  " rules enim ad minim veniam, quis nostrud fragmentContainerView ullamco laboris nisi ut aliquip ex ea" +
  " dataBinding compilerOptions consequat. Kotlin coroutine aute irure dolor in reprehenderit in ViewModel" +
  " velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat Room database non proident," +
  " sunt in culpa qui officia material design deserunt mollit anim id est laborum."

private val LIST_ITEMS =
  ANDROID_IPSUM.split(",").map { lorem ->
    lorem.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }
