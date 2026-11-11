// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.devkit.compose.showcase.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.intellij.platform.compose.swing.components.ComboBox
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.platform.compose.swing.components.style.errorOutline
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.ContentTransform
import org.jetbrains.compose.swing.animation.Crossfade
import org.jetbrains.compose.swing.animation.DimensionToVector
import org.jetbrains.compose.swing.animation.EnterTransition
import org.jetbrains.compose.swing.animation.ExitTransition
import org.jetbrains.compose.swing.animation.PointToVector
import org.jetbrains.compose.swing.animation.SizeTransform
import org.jetbrains.compose.swing.animation.animateColor
import org.jetbrains.compose.swing.animation.animateColorAsState
import org.jetbrains.compose.swing.animation.animateContentSize
import org.jetbrains.compose.swing.animation.core.Animatable
import org.jetbrains.compose.swing.animation.core.EaseInBack
import org.jetbrains.compose.swing.animation.core.EaseInOut
import org.jetbrains.compose.swing.animation.core.EaseInOutCubic
import org.jetbrains.compose.swing.animation.core.EaseOutBounce
import org.jetbrains.compose.swing.animation.core.EaseOutElastic
import org.jetbrains.compose.swing.animation.core.Easing
import org.jetbrains.compose.swing.animation.core.FastOutLinearInEasing
import org.jetbrains.compose.swing.animation.core.FastOutSlowInEasing
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.LinearOutSlowInEasing
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.RepeatMode
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.TweenSpec
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.animateIntAsState
import org.jetbrains.compose.swing.animation.core.animateValueAsState
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.keyframes
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.repeatable
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.animation.expandHorizontally
import org.jetbrains.compose.swing.animation.expandIn
import org.jetbrains.compose.swing.animation.expandVertically
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.scaleIn
import org.jetbrains.compose.swing.animation.scaleOut
import org.jetbrains.compose.swing.animation.shrinkHorizontally
import org.jetbrains.compose.swing.animation.shrinkOut
import org.jetbrains.compose.swing.animation.shrinkVertically
import org.jetbrains.compose.swing.animation.slideInHorizontally
import org.jetbrains.compose.swing.animation.slideInVertically
import org.jetbrains.compose.swing.animation.slideOutHorizontally
import org.jetbrains.compose.swing.animation.slideOutVertically
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.CircleShape
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.focusable
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.swing.animation.Animatable as ColorAnimatable

/** The Animations tab. The header with the animation settings stays at the top, above the pages of the animation showcases. */
@Composable
internal fun AnimationsTab(modifier: SwingModifier) {
  val settings = remember { AnimationSettings() }
  val pages = remember(settings) {
    listOf(
      ShowcasePage(
        "Animated elements",
        "Small controls that animate their state: a chip with a moving gradient, a check box and a toggle.",
      ) { AnimatedElementsPage(settings) },
      ShowcasePage(
        "AnimatedVisibility",
        "Animates the appearance and the disappearance of its content. Combine the enter and exit transitions.",
      ) { AnimatedVisibilityPage(settings) },
      ShowcasePage(
        "AnimatedContent",
        "Animates the change from one content to the next. The cards have different sizes.",
      ) { AnimatedContentPage(settings) },
      ShowcasePage("Crossfade", "Fades out the old content and fades in the new content.") { CrossfadePage(settings) },
      ShowcasePage(
        "animateContentSize",
        "A modifier that animates the size of a component when its content changes.",
      ) { AnimateContentSizePage(settings) },
      ShowcasePage(
        "animate*AsState",
        "Each function animates one value to its target. The target state sets all the targets.",
      ) { AnimateAsStatePage(settings) },
      ShowcasePage("updateTransition", "One transition animates several values from the same state.") { UpdateTransitionPage(settings) },
      ShowcasePage(
        "Infinite transition",
        "rememberInfiniteTransition repeats its animations until it leaves the composition.",
      ) { InfiniteTransitionPage(settings) },
      ShowcasePage("Animatable", "An Animatable holds a value. Coroutines animate it, snap it and stop it.") { AnimatablePage(settings) },
      ShowcasePage(
        "Animation specs",
        "The same motion with different specs. The spring has no duration, so the header duration does not apply.",
      ) { AnimationSpecPage(settings) },
    )
  }
  // The tab paints the panel background, so the controls of the header do not show a background of their own.
  Column(
    modifier.styleable {
      background(UIUtil.getPanelBackground())
      opaque(true)
    },
    verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp),
  ) {
    AnimationSettingsHeader(settings)
    Separator(SwingModifier.fillMaxWidth())
    ShowcasePages(pages, SwingModifier.fillMaxWidth().weight(1f))
  }
}

// region Settings

/** The settings in the header of the tab. Every showcase makes its animation specs from them. */
@Stable
private class AnimationSettings {
  var durationMillis by mutableIntStateOf(DEFAULT_DURATION)
    private set

  /** The text of the duration field. It can be invalid while the user types. Then [durationMillis] keeps the last valid value. */
  var durationText by mutableStateOf(DEFAULT_DURATION.toString())
    private set

  var easing by mutableStateOf(EasingItem.FAST_OUT_SLOW_IN)

  val durationTextValid: Boolean
    get() = durationText.trim().toIntOrNull() in 0..MAX_DURATION

  fun changeDuration(delta: Int) {
    durationMillis = (durationMillis + delta).coerceIn(0, MAX_DURATION)
    durationText = durationMillis.toString()
  }

  fun editDurationText(text: String) {
    durationText = text
    text.trim().toIntOrNull()?.takeIf { it in 0..MAX_DURATION }?.let { durationMillis = it }
  }

  fun <T> tween(): TweenSpec<T> = tween(durationMillis, easing = easing.easing)

  /** A [tween] for a repeated animation. A repeated animation needs a duration above zero. */
  fun <T> repeatedTween(): TweenSpec<T> = tween(durationMillis.coerceAtLeast(1), easing = easing.easing)
}

private enum class EasingItem(private val title: String, val easing: Easing) {
  FAST_OUT_SLOW_IN("FastOutSlowIn", FastOutSlowInEasing),
  LINEAR_OUT_SLOW_IN("LinearOutSlowIn", LinearOutSlowInEasing),
  FAST_OUT_LINEAR_IN("FastOutLinearIn", FastOutLinearInEasing),
  LINEAR("Linear", LinearEasing),
  EASE_IN_OUT("EaseInOut", EaseInOut),
  EASE_IN_OUT_CUBIC("EaseInOutCubic", EaseInOutCubic),
  EASE_IN_BACK("EaseInBack", EaseInBack),
  EASE_OUT_BOUNCE("EaseOutBounce", EaseOutBounce),
  EASE_OUT_ELASTIC("EaseOutElastic", EaseOutElastic);

  override fun toString(): String = title
}

@Composable
private fun ColumnScope.AnimationSettingsHeader(settings: AnimationSettings) {
  val gap = spacing.verticalSmallGap.dp
  Row(
    SwingModifier.fillMaxWidth().padding(start = gap * 2, top = gap, end = gap),
    horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Label("Duration (ms):")
    DurationStepButton(settings, -LARGE_STEP)
    DurationStepButton(settings, -SMALL_STEP)
    TextField(
      value = settings.durationText,
      onValueChange = settings::editDurationText,
      style = {
        errorOutline(!settings.durationTextValid)
        toolTip("From 0 to $MAX_DURATION ms")
        accessibleName("Duration")
      },
      columns = 4,
    )
    DurationStepButton(settings, SMALL_STEP)
    DurationStepButton(settings, LARGE_STEP)
    RigidArea(spacing.horizontalDefaultGap.dp, 0)
    Label("Easing:")
    ComboBox(items = EasingItem.entries, selectedItem = settings.easing, onSelectedItemChange = { if (it != null) settings.easing = it })
  }
}

/** A button that changes the duration by [delta]. The text is "-" or "+" for the small step, and "--" or "++" for the large step. */
@Composable
private fun DurationStepButton(settings: AnimationSettings, delta: Int) {
  val sign = if (delta < 0) "-" else "+"
  val text = if (abs(delta) == LARGE_STEP) "$sign$sign" else sign
  val canChange = if (delta < 0) settings.durationMillis > 0 else settings.durationMillis < MAX_DURATION
  val description = "$sign${abs(delta)} ms"
  Button(
    text = text,
    onClick = { settings.changeDuration(delta) },
    style = {
      enabled(canChange)
      toolTip(description)
      accessibleName(description)
    },
  )
}

private const val DEFAULT_DURATION = 300
private const val MAX_DURATION = 1000
private const val SMALL_STEP = 10
private const val LARGE_STEP = 50

// endregion

// region AnimatedVisibility

private enum class EnterItem(private val title: String) {
  FADE_IN("fadeIn"),
  EXPAND_IN("expandIn"),
  EXPAND_HORIZONTALLY("expandHorizontally"),
  EXPAND_VERTICALLY("expandVertically"),
  SCALE_IN("scaleIn"),
  SLIDE_IN_HORIZONTALLY("slideInHorizontally"),
  SLIDE_IN_VERTICALLY("slideInVertically");

  fun transition(settings: AnimationSettings, anchor: AnchorItem, slide: SlideItem): EnterTransition = when (this) {
    FADE_IN -> fadeIn(settings.tween())
    EXPAND_IN -> expandIn(settings.tween(), expandFrom = anchor.alignment)
    EXPAND_HORIZONTALLY -> expandHorizontally(settings.tween(), expandFrom = anchor.horizontal)
    EXPAND_VERTICALLY -> expandVertically(settings.tween(), expandFrom = anchor.vertical)
    SCALE_IN -> scaleIn(settings.tween())
    SLIDE_IN_HORIZONTALLY -> slideInHorizontally(settings.tween()) { slide.offset(it) }
    SLIDE_IN_VERTICALLY -> slideInVertically(settings.tween()) { slide.offset(it) }
  }

  override fun toString(): String = title
}

private enum class ExitItem(private val title: String) {
  FADE_OUT("fadeOut"),
  SHRINK_OUT("shrinkOut"),
  SHRINK_HORIZONTALLY("shrinkHorizontally"),
  SHRINK_VERTICALLY("shrinkVertically"),
  SCALE_OUT("scaleOut"),
  SLIDE_OUT_HORIZONTALLY("slideOutHorizontally"),
  SLIDE_OUT_VERTICALLY("slideOutVertically");

  fun transition(settings: AnimationSettings, anchor: AnchorItem, slide: SlideItem): ExitTransition = when (this) {
    FADE_OUT -> fadeOut(settings.tween())
    SHRINK_OUT -> shrinkOut(settings.tween(), shrinkTowards = anchor.alignment)
    SHRINK_HORIZONTALLY -> shrinkHorizontally(settings.tween(), shrinkTowards = anchor.horizontal)
    SHRINK_VERTICALLY -> shrinkVertically(settings.tween(), shrinkTowards = anchor.vertical)
    SCALE_OUT -> scaleOut(settings.tween())
    SLIDE_OUT_HORIZONTALLY -> slideOutHorizontally(settings.tween()) { slide.offset(it) }
    SLIDE_OUT_VERTICALLY -> slideOutVertically(settings.tween()) { slide.offset(it) }
  }

  override fun toString(): String = title
}

/** The side that an expand transition starts from and that a shrink transition ends at. */
private enum class AnchorItem(
  private val title: String,
  val horizontal: Alignment.Horizontal,
  val vertical: Alignment.Vertical,
  val alignment: Alignment,
) {
  START("Start / Top", Alignment.Start, Alignment.Top, Alignment.TopStart),
  CENTER("Center", Alignment.CenterHorizontally, Alignment.CenterVertically, Alignment.Center),
  END("End / Bottom", Alignment.End, Alignment.Bottom, Alignment.BottomEnd);

  override fun toString(): String = title
}

/** The side that a slide transition comes from and goes to. */
private enum class SlideItem(private val title: String, private val sign: Int) {
  START("Start / Top", -1),
  END("End / Bottom", 1);

  fun offset(fullSize: Int): Int = sign * fullSize

  override fun toString(): String = title
}

/** An item in the middle of the AnimatedVisibility list. It enters when it is created. */
@Stable
private class ListItem(val id: Int) {
  val visibleState = MutableTransitionState(false).apply { targetState = true }
}

@Composable
private fun ColumnScope.AnimatedVisibilityPage(settings: AnimationSettings) {
  var enter by remember { mutableStateOf(setOf(EnterItem.FADE_IN, EnterItem.EXPAND_VERTICALLY)) }
  var exit by remember { mutableStateOf(setOf(ExitItem.FADE_OUT, ExitItem.SHRINK_VERTICALLY)) }
  var anchor by remember { mutableStateOf(AnchorItem.END) }
  var slide by remember { mutableStateOf(SlideItem.START) }

  Row(horizontalArrangement = Arrangement.spacedBy(spacing.horizontalColumnsGap.dp)) {
    TransitionCheckBoxes("Enter", EnterItem.entries, enter) { enter = it }
    TransitionCheckBoxes("Exit", ExitItem.entries, exit) { exit = it }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp)) {
      Label("Expand from, shrink to:")
      ComboBox(items = AnchorItem.entries, selectedItem = anchor, onSelectedItemChange = { if (it != null) anchor = it })
      Label("Slide from, slide to:")
      ComboBox(items = SlideItem.entries, selectedItem = slide, onSelectedItemChange = { if (it != null) slide = it })
    }
  }

  val enterItems = EnterItem.entries.filter { it in enter }
  val exitItems = ExitItem.entries.filter { it in exit }
  val enterTransition = enterItems.fold(EnterTransition.None) { result, item -> result + item.transition(settings, anchor, slide) }
  val exitTransition = exitItems.fold(ExitTransition.None) { result, item -> result + item.transition(settings, anchor, slide) }
  Comment("enter = ${transitionText(enterItems, "EnterTransition.None")}<br>exit = ${transitionText(exitItems, "ExitTransition.None")}")

  AnimatedVisibilityList(enterTransition, exitTransition)
}

/** A column with a label and one check box for each of the [items]. */
@Composable
private fun <T> TransitionCheckBoxes(label: String, items: List<T>, selected: Set<T>, onSelectedChange: (Set<T>) -> Unit) {
  Column {
    Label("$label:")
    for (item in items) {
      CheckBox(
        text = item.toString(),
        checked = item in selected,
        onCheckedChange = { onSelectedChange(if (it) selected + item else selected - item) },
      )
    }
  }
}

private fun transitionText(items: List<Any>, none: String): String =
  if (items.isEmpty()) none else items.joinToString(" + ") { "$it()" }

/**
 * A list with a static first and last item. The buttons add an item to the end of the middle part, and remove the last item of it.
 * An item that leaves stays in the list until its exit animation ends.
 */
@Composable
private fun ColumnScope.AnimatedVisibilityList(enter: EnterTransition, exit: ExitTransition) {
  val items = remember { mutableStateListOf(ListItem(1), ListItem(2)) }
  var nextId by remember { mutableIntStateOf(3) }
  val lastShown = items.lastOrNull { it.visibleState.targetState }

  ControlsRow(fillWidth = false) {
    Button(text = "Add", onClick = { items.add(ListItem(nextId++)) })
    Button(text = "Remove", onClick = { lastShown?.visibleState?.targetState = false }, style = { enabled(lastShown != null) })
  }

  Column(SwingModifier.fillMaxWidth().demoArea(), verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp)) {
    DemoCell("First item (static)", SwingModifier.fillMaxWidth().styleable { enabled(false) })
    for (item in items) {
      key(item.id) {
        // No fillMaxWidth here: a fixed width would override the animated width of expandHorizontally and shrinkHorizontally.
        AnimatedVisibility(visibleState = item.visibleState, enter = enter, exit = exit) {
          DemoCell("Item ${item.id}", SwingModifier.fillMaxWidth())
        }
        if (!item.visibleState.targetState) {
          LaunchedEffect(Unit) {
            snapshotFlow { item.visibleState.isIdle }.first { it }
            items.remove(item)
          }
        }
      }
    }
    DemoCell("Last item (static)", SwingModifier.fillMaxWidth().styleable { enabled(false) })
  }
}

// endregion

// region AnimatedContent, Crossfade, animateContentSize

private enum class ContentTransitionItem(private val title: String) {
  FADE("Fade"),
  FADE_SCALE("Fade + scale"),
  SLIDE_HORIZONTALLY("Slide horizontally"),
  SLIDE_VERTICALLY("Slide vertically"),
  EXPAND("Expand / shrink"),
  NONE("None");

  fun transform(scope: AnimatedContentTransitionScope<Int>, settings: AnimationSettings): ContentTransform {
    val forward = scope.targetState > scope.initialState
    return when (this) {
      FADE -> fadeIn(settings.tween()) togetherWith fadeOut(settings.tween())
      FADE_SCALE -> (fadeIn(settings.tween()) + scaleIn(settings.tween(), initialScale = 0.9f)) togetherWith fadeOut(settings.tween())
      SLIDE_HORIZONTALLY -> {
        val direction = if (forward) SlideDirection.Start else SlideDirection.End
        scope.slideIntoContainer(direction, settings.tween()) togetherWith scope.slideOutOfContainer(direction, settings.tween())
      }
      SLIDE_VERTICALLY -> {
        val direction = if (forward) SlideDirection.Up else SlideDirection.Down
        scope.slideIntoContainer(direction, settings.tween()) togetherWith scope.slideOutOfContainer(direction, settings.tween())
      }
      EXPAND -> (fadeIn(settings.tween()) + expandVertically(settings.tween())) togetherWith
        (fadeOut(settings.tween()) + shrinkVertically(settings.tween()))
      NONE -> EnterTransition.None togetherWith ExitTransition.None
    }
  }

  override fun toString(): String = title
}

private enum class ContentAlignmentItem(private val title: String, val alignment: Alignment) {
  TOP_START("TopStart", Alignment.TopStart),
  CENTER("Center", Alignment.Center),
  BOTTOM_END("BottomEnd", Alignment.BottomEnd);

  override fun toString(): String = title
}

/** The cards of the AnimatedContent showcase. They have different sizes, so the size animation is visible. */
private val CARD_SIZES = listOf(Dimension(120, 40), Dimension(260, 70), Dimension(180, 110), Dimension(320, 50))

@Composable
private fun ColumnScope.AnimatedContentPage(settings: AnimationSettings) {
  var card by remember { mutableIntStateOf(0) }
  var transition by remember { mutableStateOf(ContentTransitionItem.SLIDE_HORIZONTALLY) }
  var alignment by remember { mutableStateOf(ContentAlignmentItem.TOP_START) }
  var sizeTransform by remember { mutableStateOf(true) }
  var clip by remember { mutableStateOf(true) }

  Selector("Transition", ContentTransitionItem.entries, transition) { transition = it }
  Selector("contentAlignment", ContentAlignmentItem.entries, alignment) { alignment = it }
  LabeledRow("SizeTransform") {
    CheckBox(text = "Animate the size", checked = sizeTransform, onCheckedChange = { sizeTransform = it })
    CheckBox(text = "clip", checked = clip, onCheckedChange = { clip = it }, style = { enabled(sizeTransform) })
  }
  ControlsRow(fillWidth = false) {
    Button(text = "Previous", onClick = { card = (card + CARD_SIZES.size - 1) % CARD_SIZES.size })
    Button(text = "Next", onClick = { card = (card + 1) % CARD_SIZES.size })
  }

  Column(SwingModifier.fillMaxWidth().height(130.dp)) {
    AnimatedContent(
      targetState = card,
      modifier = SwingModifier.demoArea(),
      transitionSpec = {
        val size = if (sizeTransform) SizeTransform(clip = clip, sizeAnimationSpec = { _, _ -> settings.tween() }) else null
        transition.transform(this, settings).using(size)
      },
      contentAlignment = alignment.alignment,
      label = "AnimatedContent showcase",
    ) { index ->
      val size = CARD_SIZES[index]
      DemoCell("Card ${index + 1}", SwingModifier.size(size.width.dp, size.height.dp))
    }
  }
}

private enum class CrossfadeTarget(private val title: String) {
  LABEL("Label"),
  BUTTONS("Buttons"),
  PROGRESS("Progress bar");

  override fun toString(): String = title
}

@Composable
private fun ColumnScope.CrossfadePage(settings: AnimationSettings) {
  var target by remember { mutableStateOf(CrossfadeTarget.LABEL) }
  Selector("Target state", CrossfadeTarget.entries, target) { target = it }
  Crossfade(
    targetState = target,
    modifier = SwingModifier.fillMaxWidth().height(40.dp),
    animationSpec = settings.tween(),
    label = "Crossfade",
  ) {
    when (it) {
      CrossfadeTarget.LABEL -> DemoCell("A label in a cell")
      CrossfadeTarget.BUTTONS -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp)) {
        Button(text = "First", onClick = {})
        Button(text = "Second", onClick = {})
      }
      CrossfadeTarget.PROGRESS -> ProgressBar(value = 60, stringPainted = true)
    }
  }
}

@Composable
private fun ColumnScope.AnimateContentSizePage(settings: AnimationSettings) {
  var expanded by remember { mutableStateOf(false) }
  var alignment by remember { mutableStateOf(ContentAlignmentItem.TOP_START) }
  Selector("alignment", ContentAlignmentItem.entries, alignment) { alignment = it }
  CheckBox(text = "Show more content", checked = expanded, onCheckedChange = { expanded = it })
  Comment("The colored box animates its size. The frame keeps the height of the expanded box, so the page does not move")
  // The background comes after animateContentSize, so the animated clip also clips the background. Then the box shows its size
  // while it shrinks, after the extra cells are gone.
  Box(SwingModifier.fillMaxWidth().height(ANIMATED_SIZE_AREA_HEIGHT.dp).demoArea()) {
    Column(
      SwingModifier
        .animateContentSize(settings.tween(), alignment = alignment.alignment)
        .background(SECOND_COLOR, RoundedCornerShape(SIZE_BOX_ARC.dp.toFloat()))
        .padding(all = spacing.verticalComponentGap.dp),
      verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp),
    ) {
      DemoCell("This cell is always shown")
      if (expanded) {
        DemoCell("An extra cell")
        DemoCell("Another extra cell, which is wider than the others")
      }
    }
  }
}

private const val ANIMATED_SIZE_AREA_HEIGHT = 130
private const val SIZE_BOX_ARC = 8

// endregion

// region Values

private val FIRST_COLOR: Color = JBUI.CurrentTheme.Banner.INFO_BACKGROUND
private val SECOND_COLOR: Color = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND

@Composable
private fun ColumnScope.AnimateAsStatePage(settings: AnimationSettings) {
  var target by remember { mutableStateOf(false) }
  CheckBox(text = "Target state", checked = target, onCheckedChange = { target = it })

  val progress by animateIntAsState(if (target) 100 else 0, settings.tween(), label = "progress")
  LabeledRow("animateIntAsState") {
    ProgressBar(value = progress, stringPainted = true)
  }

  val color by animateColorAsState(if (target) SECOND_COLOR else FIRST_COLOR, settings.tween(), label = "color")
  LabeledRow("animateColorAsState") {
    DemoCell("Background", SwingModifier.width(160.dp), background = color)
  }

  val size by animateValueAsState(
    if (target) Dimension(260.dp, 60.dp) else Dimension(100.dp, 30.dp),
    DimensionToVector,
    settings.tween(),
    label = "size",
  )
  LabeledRow("animateValueAsState(Dimension)") {
    Box(SwingModifier.height(60.dp)) {
      DemoCell("Size", SwingModifier.size(size.width, size.height))
    }
  }

  val offset by animateValueAsState(if (target) Point(240.dp, 0) else Point(0, 0), PointToVector, settings.tween(), label = "offset")
  LabeledRow("animateValueAsState(Point)") {
    Box(SwingModifier.weight(1f)) {
      DemoCell("Offset", SwingModifier.offset(offset.x, offset.y))
    }
  }
}

@Composable
private fun ColumnScope.UpdateTransitionPage(settings: AnimationSettings) {
  var expanded by remember { mutableStateOf(false) }
  CheckBox(text = "Expanded", checked = expanded, onCheckedChange = { expanded = it })

  val transition = updateTransition(expanded, label = "expanded")
  val width by transition.animateInt(transitionSpec = { settings.tween() }, label = "width") { if (it) 320.dp else 100.dp }
  val progress by transition.animateInt(transitionSpec = { settings.tween() }, label = "progress") { if (it) 100 else 0 }
  val color by transition.animateColor(transitionSpec = { settings.tween() }, label = "color") { if (it) SECOND_COLOR else FIRST_COLOR }

  Comment("One state drives the width, the color and the progress at the same time")
  DemoCell("Width and color", SwingModifier.width(width), background = color)
  ProgressBar(value = progress, stringPainted = true)
  Label("currentState = ${transition.currentState}, targetState = ${transition.targetState}, isRunning = ${transition.isRunning}")
}

/** The length of one cycle of the infinite transition, as a multiple of the header duration. */
private enum class CycleItem(val factor: Int) {
  X1(1),
  X2(2),
  X4(4),
  X8(8);

  override fun toString(): String = "$factor×"
}

@Composable
private fun ColumnScope.InfiniteTransitionPage(settings: AnimationSettings) {
  var running by remember { mutableStateOf(true) }
  var repeatMode by remember { mutableStateOf(RepeatMode.Reverse) }
  var cycle by remember { mutableStateOf(CycleItem.X4) }
  val cycleMillis = (settings.durationMillis * cycle.factor).coerceAtLeast(1)
  Selector("RepeatMode", RepeatMode.entries, repeatMode) { repeatMode = it }
  Selector("Cycle length", CycleItem.entries, cycle) { cycle = it }
  Comment("One cycle takes $cycleMillis ms")
  CheckBox(text = "Running", checked = running, onCheckedChange = { running = it })
  if (running) {
    // An infinite transition keeps the spec it starts with. A new key starts a new transition with the new spec.
    key(repeatMode, cycleMillis, settings.easing) {
      InfiniteAnimation(tween(cycleMillis, easing = settings.easing.easing), repeatMode)
    }
  }
  else {
    Label("Stopped. The transition leaves the composition.")
  }
}

@Composable
private fun ColumnScope.InfiniteAnimation(spec: TweenSpec<Float>, repeatMode: RepeatMode) {
  val transition = rememberInfiniteTransition(label = "infinite")
  val progress by transition.animateFloat(0f, 100f, infiniteRepeatable(spec, repeatMode), label = "progress")
  val colorSpec = tween<Color>(spec.durationMillis, easing = spec.easing)
  val color by transition.animateColor(FIRST_COLOR, SECOND_COLOR, infiniteRepeatable(colorSpec, repeatMode), label = "color")
  ProgressBar(value = progress.toInt(), stringPainted = true)
  DemoCell("Color", SwingModifier.width(160.dp), background = color)
}

@Composable
private fun ColumnScope.AnimatablePage(settings: AnimationSettings) {
  val animatable = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  var target by remember { mutableIntStateOf(80) }

  LabeledRow("Target value") {
    Slider(value = target, onValueChange = { target = it })
    Label("$target")
  }
  ControlsRow(fillWidth = false) {
    Button(text = "Animate to", onClick = { scope.launch { animatable.animateTo(target.toFloat(), settings.tween()) } })
    Button(text = "Snap to", onClick = { scope.launch { animatable.snapTo(target.toFloat()) } })
    Button(text = "Stop", onClick = { scope.launch { animatable.stop() } })
  }
  ProgressBar(value = animatable.value.toInt(), stringPainted = true)
  Label("value = ${"%.1f".format(animatable.value)}, isRunning = ${animatable.isRunning}")
}

// endregion

// region Animation specs

private enum class DampingItem(private val title: String, val ratio: Float) {
  NO_BOUNCY("NoBouncy", Spring.DampingRatioNoBouncy),
  LOW_BOUNCY("LowBouncy", Spring.DampingRatioLowBouncy),
  MEDIUM_BOUNCY("MediumBouncy", Spring.DampingRatioMediumBouncy),
  HIGH_BOUNCY("HighBouncy", Spring.DampingRatioHighBouncy);

  override fun toString(): String = title
}

private enum class StiffnessItem(private val title: String, val stiffness: Float) {
  HIGH("High", Spring.StiffnessHigh),
  MEDIUM("Medium", Spring.StiffnessMedium),
  MEDIUM_LOW("MediumLow", Spring.StiffnessMediumLow),
  LOW("Low", Spring.StiffnessLow),
  VERY_LOW("VeryLow", Spring.StiffnessVeryLow);

  override fun toString(): String = title
}

@Composable
private fun ColumnScope.AnimationSpecPage(settings: AnimationSettings) {
  var target by remember { mutableStateOf(false) }
  var damping by remember { mutableStateOf(DampingItem.MEDIUM_BOUNCY) }
  var stiffness by remember { mutableStateOf(StiffnessItem.LOW) }

  Selector("Spring damping", DampingItem.entries, damping) { damping = it }
  Selector("Spring stiffness", StiffnessItem.entries, stiffness) { stiffness = it }
  CheckBox(text = "Target state", checked = target, onCheckedChange = { target = it })

  val targetValue = if (target) 100 else 0
  val duration = settings.durationMillis
  val tweenValue by animateIntAsState(targetValue, settings.tween(), label = "tween")
  val springValue by animateIntAsState(targetValue, spring(damping.ratio, stiffness.stiffness), label = "spring")
  val keyframesValue by animateIntAsState(
    targetValue,
    keyframes {
      durationMillis = duration
      (if (target) 80 else 20) at duration / 4
      (if (target) 90 else 10) at duration * 3 / 4
    },
    label = "keyframes",
  )
  val repeatableValue by animateIntAsState(targetValue, repeatable(3, settings.repeatedTween(), RepeatMode.Reverse), label = "repeatable")

  LabeledRow("tween") { Track(tweenValue) }
  LabeledRow("spring") { Track(springValue) }
  LabeledRow("keyframes") { Track(keyframesValue) }
  LabeledRow("repeatable(3, Reverse)") { Track(repeatableValue) }
}

/** A track with a cell at [value] percent of its width. A spring can move the cell past the ends. */
@Composable
private fun RowScope.Track(value: Int) {
  val trackWidth = TRACK_WIDTH.dp
  Box(SwingModifier.width(trackWidth + TRACK_CELL_SIZE.dp).height(TRACK_CELL_SIZE.dp).demoArea()) {
    DemoCell("", SwingModifier.size(TRACK_CELL_SIZE.dp, TRACK_CELL_SIZE.dp).offset(x = value * trackWidth / 100))
  }
}

private const val TRACK_WIDTH = 300
private const val TRACK_CELL_SIZE = 20

// endregion

// region Animated elements

@Composable
private fun ColumnScope.AnimatedElementsPage(settings: AnimationSettings) {
  Comment("The header duration sets the speed of the gradient, the check mark and the toggle. The check box press always takes 80 ms")

  LabeledRow("Chip") {
    NewChip(settings)
  }

  var checked by remember { mutableStateOf(true) }
  LabeledRow("Check box") {
    AnimatedCheckBox(text = "Animated check box", checked = checked, onCheckedChange = { checked = it }, settings = settings)
    CheckBox(text = "Swing check box", checked = checked, onCheckedChange = { checked = it })
  }

  var on by remember { mutableStateOf(true) }
  LabeledRow("Toggle") {
    OnOffSwitch(text = if (on) "On" else "Off", checked = on, onCheckedChange = { on = it }, settings = settings)
    // The Swing toggle that OnOffSwitch copies. In an Islands theme, IslandsOnOffButtonUI paints it.
    ClickableControl("Swing OnOffButton", onClick = { on = !on }) {
      SwingOnOffButton(checked = on, onCheckedChange = { on = it }, style = { accessibleName("Swing OnOffButton") })
    }
  }
}

/** The platform [OnOffButton], selected while [checked]. */
@Composable
private fun SwingOnOffButton(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: SwingModifier = SwingModifier,
  style: SwingStyle<ContainerStyleScope> = SwingStyle,
) {
  SwingNode(
    factory = { OnOffButton() },
    modifier = modifier.actionListener<OnOffButton> { onCheckedChange(isSelected) }.styleable(style),
    update = { set(checked) { isSelected = it } },
  )
}

/** The pairs of gradient colors that [NewChip] goes through. */
private val CHIP_GRADIENTS: List<Pair<Color, Color>> = listOf(
  JBColor(0x6B57FF, 0x6B57FF) to JBColor(0xFF45ED, 0xFF45ED),
  JBColor(0xFF318C, 0xFF318C) to JBColor(0xFDB60D, 0xFDB60D),
  JBColor(0x21D789, 0x21D789) to JBColor(0x07C3F2, 0x07C3F2),
)

/** The text color of [NewChip]. It is white in every theme. `JBColor.WHITE` is dark in a dark theme. */
private val CHIP_TEXT: Color = JBColor(0xFFFFFF, 0xFFFFFF)

/** Darkens the gradient of [NewChip], so the white text stays readable. */
private val CHIP_SHADE: Color = ColorUtil.withAlpha(JBColor(0x000000, 0x000000), 0.25)

/** The shortest step of the [NewChip] gradient. A shorter step makes the colors flicker. */
private const val MIN_CHIP_STEP = 50

/** A "NEW" chip. Its gradient moves through the colors of [CHIP_GRADIENTS]. */
@Composable
private fun NewChip(settings: AnimationSettings) {
  val firstColor = remember { ColorAnimatable(CHIP_GRADIENTS[0].first) }
  val secondColor = remember { ColorAnimatable(CHIP_GRADIENTS[0].second) }
  val step = settings.durationMillis.coerceAtLeast(MIN_CHIP_STEP)

  LaunchedEffect(step) {
    // All the gradient colors in one sequence. The second color stays one position ahead of the first color.
    val targets = CHIP_GRADIENTS.flatMap { listOf(it.first, it.second) }
    val spec = tween<Color>(step * 2, easing = LinearEasing)
    var index = 0
    while (true) {
      val nextFirst = targets[(index + 1) % targets.size]
      val nextSecond = targets[(index + 2) % targets.size]
      launch { firstColor.animateTo(nextFirst, spec) }
      launch { secondColor.animateTo(nextSecond, spec) }
      delay(step.milliseconds)
      index = (index + 1) % targets.size
    }
  }

  val gradient = Brush.horizontalGradient(0f to firstColor.value, 1f to secondColor.value)
  Box(SwingModifier.background(gradient, CircleShape).background(CHIP_SHADE, CircleShape)) {
    Label(
      "NEW",
      modifier = SwingModifier.padding(vertical = 2.dp, horizontal = 8.dp),
      style = {
        foreground(CHIP_TEXT)
        font(JBFont.small().asBold())
      },
    )
  }
}

/**
 * A row with the [control] and the [text] after it. A click on the control or on the text calls [onClick]. [onPressedChange] reports
 * when the mouse button goes down and up on the row.
 */
@Composable
private fun ClickableControl(
  text: String,
  onClick: () -> Unit,
  onPressedChange: (Boolean) -> Unit = {},
  control: @Composable RowScope.() -> Unit,
) {
  // The row gets the mouse events of a child that does not listen to the mouse, such as the text. A Swing control handles its own clicks.
  Row(
    SwingModifier
      .onPointerEvent(onPress = { onPressedChange(true) }, onRelease = { onPressedChange(false) }, onClick = { onClick() })
      .styleable { cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)) },
    horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    control()
    Label(text)
  }
}

/** The unscaled geometry of [AnimatedCheckBox]. */
private object CheckBoxGeometry {
  const val SIZE = 20f
  const val BOX = 16f
  const val ARC = 5f
  const val MARK_STROKE = 1.75f
  const val FOCUS_STROKE = 2f
  val MARK_START = Point2D.Float(4.5f, 8.5f)
  val MARK_CORNER = Point2D.Float(7f, 11f)
  val MARK_END = Point2D.Float(11.5f, 5.5f)
}

/** The colors of [AnimatedCheckBox]. A selected box takes the colors of a toggle that is on. */
private object CheckBoxColors {
  val background: Color = JBColor.namedColor("TextField.background", UIUtil.getTextFieldBackground())
  val border: Color = JBColor.namedColor("Component.borderColor", JBColor.border())
  val selectedBackground: Color get() = OnOffSwitchColors.onBackground
  val selectedBorder: Color get() = OnOffSwitchColors.onBorder
  val mark: Color get() = OnOffSwitchColors.onNotch
  val focus: Color get() = OnOffSwitchColors.focusBorder
}

/**
 * A check box that draws itself. The box shrinks while the mouse button is down. The fill fades in, and the check mark draws itself
 * from start to end.
 */
@Composable
private fun AnimatedCheckBox(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, settings: AnimationSettings) {
  var pressed by remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }
  val currentChecked by rememberUpdatedState(checked)
  val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
  val toggle = { currentOnCheckedChange(!currentChecked) }

  val colors = CheckBoxColors
  val scale by animateFloatAsState(if (pressed) 0.75f else 1f, tween(80, easing = LinearEasing), label = "scale")
  val background by animateColorAsState(if (checked) colors.selectedBackground else colors.background, settings.tween(), label = "fill")
  val border by animateColorAsState(if (checked) colors.selectedBorder else colors.border, settings.tween(), label = "border")
  val markProgress by animateFloatAsState(if (checked) 1f else 0f, settings.tween(), label = "mark")

  ClickableControl(text, onClick = toggle, onPressedChange = { pressed = it }) {
    val size = JBUIScale.scale(CheckBoxGeometry.SIZE).toInt()
    Canvas(
      SwingModifier
        .onKeyStroke("SPACE") { toggle() }
        .onFocus(onGained = { focused = true }, onLost = { focused = false })
        .styleable {
          preferredSize(size, size)
          focusable(true)
          accessibleName(text)
        },
    ) {
      val g2 = graphics.create() as Graphics2D
      try {
        with(CheckBoxGeometry) {
          val unit = JBUIScale.scale(1f)
          g2.translate(width / 2.0, height / 2.0)
          g2.scale((unit * scale).toDouble(), (unit * scale).toDouble())
          g2.translate(-BOX / 2.0, -BOX / 2.0)

          g2.color = background
          g2.fill(RoundRectangle2D.Float(0f, 0f, BOX, BOX, ARC, ARC))
          g2.color = border
          g2.stroke = BasicStroke(1f)
          g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, BOX - 1f, BOX - 1f, ARC - 1f, ARC - 1f))

          if (markProgress > 0f) {
            g2.color = colors.mark
            g2.stroke = BasicStroke(MARK_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.draw(checkMark(markProgress))
          }

          if (focused) {
            val inset = (SIZE - BOX) / 2f
            g2.color = colors.focus
            g2.stroke = BasicStroke(FOCUS_STROKE)
            g2.draw(RoundRectangle2D.Float(-inset + 1f, -inset + 1f, SIZE - 2f, SIZE - 2f, ARC + inset, ARC + inset))
          }
        }
      }
      finally {
        g2.dispose()
      }
    }
  }
}

/** The part of the check mark that [progress] covers, from `0f` for no mark to `1f` for the full mark. */
private fun checkMark(progress: Float): Path2D {
  val start = CheckBoxGeometry.MARK_START
  val corner = CheckBoxGeometry.MARK_CORNER
  val end = CheckBoxGeometry.MARK_END
  val first = start.distance(corner).toFloat()
  val second = corner.distance(end).toFloat()
  val length = (first + second) * progress.coerceIn(0f, 1f)

  val path = Path2D.Float()
  path.moveTo(start.x, start.y)
  if (length <= first) {
    val fraction = length / first
    path.lineTo(start.x + (corner.x - start.x) * fraction, start.y + (corner.y - start.y) * fraction)
    return path
  }
  val fraction = (length - first) / second
  path.lineTo(corner.x, corner.y)
  path.lineTo(corner.x + (end.x - corner.x) * fraction, corner.y + (end.y - corner.y) * fraction)
  return path
}

/** The unscaled geometry of the Islands `toggle*.svg` assets. See [com.intellij.ide.ui.laf.darcula.ui.IslandsOnOffButtonUI]. */
private object OnOffSwitchGeometry {
  const val WIDTH = 32f
  const val HEIGHT = 22f
  const val TRACK_X = 3f
  const val TRACK_Y = 3f
  const val TRACK_WIDTH = 26f
  const val TRACK_HEIGHT = 16f
  const val NOTCH_OFF_CENTER_X = 11f
  const val NOTCH_ON_CENTER_X = 21f
  const val NOTCH_CENTER_Y = 11f
  const val NOTCH_OFF_OUTER_RADIUS = 4f
  const val NOTCH_OFF_INNER_RADIUS = 2f
  const val NOTCH_ON_OUTER_RADIUS = 5f
  const val NOTCH_ON_INNER_RADIUS = 0f
  const val FOCUS_INSET = 1f
  const val FOCUS_STROKE = 2f
}

private object OnOffSwitchColors {
  val onBackground: Color = palette("toggle-on-bg", "ToggleButton.onBackground")
  val onBorder: Color = palette("toggle-on-border", "ToggleButton.borderColor")
  val onNotch: Color = palette("toggle-on-notch", "ToggleButton.onForeground")
  val offBackground: Color = palette("toggle-off-bg", "ToggleButton.offBackground")
  val offBorder: Color = palette("toggle-off-border", "ToggleButton.borderColor")
  val offNotch: Color = palette("toggle-off-notch", "ToggleButton.offForeground")
  val focusBorder: Color = palette("toggle-focus-border", JBColor.lazy { JBUI.CurrentTheme.Focus.focusColor() })

  private fun palette(key: @NonNls String, fallbackKey: @NonNls String): Color = palette(key, JBColor.namedColor(fallbackKey))

  private fun palette(key: @NonNls String, fallback: Color): Color = JBColor.namedColor("ColorPalette.$key", fallback)
}

/**
 * A copy of [com.intellij.ide.ui.laf.darcula.ui.IslandsOnOffButtonUI], with a [text] after it. The notch slides to the other side, and
 * it changes from a ring into a filled circle.
 */
@Composable
private fun OnOffSwitch(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, settings: AnimationSettings) {
  var isFocused by remember { mutableStateOf(false) }
  val currentChecked by rememberUpdatedState(checked)
  val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
  val toggle = { currentOnCheckedChange(!currentChecked) }

  val colors = OnOffSwitchColors
  val background by animateColorAsState(if (checked) colors.onBackground else colors.offBackground, settings.tween(), label = "background")
  val border by animateColorAsState(if (checked) colors.onBorder else colors.offBorder, settings.tween(), label = "border")
  val notch by animateColorAsState(if (checked) colors.onNotch else colors.offNotch, settings.tween(), label = "notch")
  with(OnOffSwitchGeometry) {
    val notchCenterX by animateFloatAsState(if (checked) NOTCH_ON_CENTER_X else NOTCH_OFF_CENTER_X, settings.tween(), label = "x")
    val notchOuterRadius by animateFloatAsState(
      if (checked) NOTCH_ON_OUTER_RADIUS else NOTCH_OFF_OUTER_RADIUS,
      settings.tween(),
      label = "outer radius",
    )
    val notchInnerRadius by animateFloatAsState(
      if (checked) NOTCH_ON_INNER_RADIUS else NOTCH_OFF_INNER_RADIUS,
      settings.tween(),
      label = "inner radius",
    )

    ClickableControl(text, onClick = toggle) {
      Canvas(
        SwingModifier
          .onKeyStroke("SPACE") { toggle() }
          .onFocus(onGained = { isFocused = true }, onLost = { isFocused = false })
          .styleable {
            preferredSize(JBUIScale.scale(WIDTH).toInt(), JBUIScale.scale(HEIGHT).toInt())
            focusable(true)
            accessibleName(text)
          },
      ) {
        val g2 = graphics.create() as Graphics2D
        try {
          val scale = JBUIScale.scale(1f)
          g2.translate((width - scale * WIDTH) / 2.0, (height - scale * HEIGHT) / 2.0)
          g2.scale(scale.toDouble(), scale.toDouble())

          g2.color = background
          g2.fill(RoundRectangle2D.Float(TRACK_X, TRACK_Y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT))
          g2.color = border
          g2.stroke = BasicStroke(1f)
          val arc = TRACK_HEIGHT - 1f
          g2.draw(RoundRectangle2D.Float(TRACK_X + 0.5f, TRACK_Y + 0.5f, TRACK_WIDTH - 1f, TRACK_HEIGHT - 1f, arc, arc))

          val notchShape = Area(notchCircle(notchCenterX, notchOuterRadius))
          if (notchInnerRadius > 0f) notchShape.subtract(Area(notchCircle(notchCenterX, notchInnerRadius)))
          g2.color = notch
          g2.fill(notchShape)

          if (isFocused) {
            g2.color = colors.focusBorder
            g2.stroke = BasicStroke(FOCUS_STROKE)
            val ringWidth = WIDTH - FOCUS_INSET * 2
            val ringHeight = HEIGHT - FOCUS_INSET * 2
            g2.draw(RoundRectangle2D.Float(FOCUS_INSET, FOCUS_INSET, ringWidth, ringHeight, ringHeight, ringHeight))
          }
        }
        finally {
          g2.dispose()
        }
      }
    }
  }
}

private fun notchCircle(centerX: Float, radius: Float): Ellipse2D =
  Ellipse2D.Float(centerX - radius, OnOffSwitchGeometry.NOTCH_CENTER_Y - radius, radius * 2, radius * 2)

// endregion
