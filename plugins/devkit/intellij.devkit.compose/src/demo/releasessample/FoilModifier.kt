package com.intellij.devkit.compose.demo.releasessample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

@Language("GLSL") // Technically, SkSL
private const val FOIL_SHADER_CODE =
  """
const float SCALE = 1.8; // Effect scale (> 1 means smaller rainbow)
const float SATURATION = 0.9; // Color saturation (0.0 = grayscale, 1.0 = full color)
const float LIGHTNESS = 0.65; // Color lightness (0.0 = black, 1.0 = white)
 
uniform shader content; // Input texture (the application canvas)
uniform vec2 resolution;  // Size of the canvas
uniform vec2 offset;     // Additional offset of the effect
uniform float intensity; // 0.0 = no effect, 1.0 = full effect

// From https://www.ryanjuckett.com/photoshop-blend-modes-in-hlsl/
vec3 BlendMode_Screen(vec3 base, vec3 blend) {
	return base + blend - base * blend;
}

vec4 rainbowEffect(vec2 uv, vec2 coord, vec2 offset) {
    vec4 srcColor = content.eval(coord);
    if (srcColor.a == 0.0) return srcColor;
    
    float hue = uv.x / (1.75 + abs(offset.x)) + offset.x / 3.0;
    float lightness = LIGHTNESS + 0.25 * (0.5 + offset.y * (0.5 - uv.y));
    hue = fract(hue);

    float c = (1.0 - abs(2.0 * lightness - 1.0)) * SATURATION;
    float x = c * (1.0 - abs(mod(hue / (1.0 / 6.0), 2.0) - 1.0));
    float m = LIGHTNESS - c / 2.0;

    vec3 rainbowPrime;

    if (hue < 1.0 / 6.0) {
        rainbowPrime = vec3(c, x, 0.0);
    } else if (hue < 1.0 / 3.0) {
        rainbowPrime = vec3(x, c, 0.0);
    } else if (hue < 0.5) {
        rainbowPrime = vec3(0.0, c, x);
    } else if (hue < 2.0 / 3.0) {
        rainbowPrime = vec3(0.0, x, c);
    } else if (hue < 5.0 / 6.0) {
        rainbowPrime = vec3(x, 0.0, c);
    } else {
        rainbowPrime = vec3(c, 0.0, x);
    }

    vec3 rainbow = BlendMode_Screen(srcColor.rgb, rainbowPrime + m);
    return mix(srcColor, vec4(rainbow, srcColor.a), intensity);
}

vec4 chromaticAberration(vec2 coord, vec2 offset) {
    vec2 uv = coord / (resolution / SCALE);
    vec4 srcColor = rainbowEffect(uv, coord, offset);
    vec2 shift = offset * vec2(3.0, 5.0) / 1000.0;
    vec4 leftColor = rainbowEffect(uv - shift, coord, offset);
    vec4 rightColor = rainbowEffect(uv + shift, coord , offset);

    return vec4(rightColor.r, srcColor.g, leftColor.b, srcColor.a);
}

vec4 main(float2 fragCoord) {
    return chromaticAberration(fragCoord.xy, offset);
}
"""

private val runtimeEffect = RuntimeEffect.makeForShader(FOIL_SHADER_CODE)
private val shaderBuilder = RuntimeShaderBuilder(runtimeEffect)

internal fun Modifier.holoFoil(normalizedOffset: Offset, intensity: Float = 1f) = graphicsLayer {
  shaderBuilder.uniform("resolution", size.width, size.height)
  shaderBuilder.uniform("offset", normalizedOffset.x, normalizedOffset.y)
  shaderBuilder.uniform("intensity", intensity * .65f)

  renderEffect =
    ImageFilter.makeRuntimeShader(
      runtimeShaderBuilder = shaderBuilder,
      shaderNames = arrayOf("content"),
      inputs = arrayOf(null),
    )
      .asComposeRenderEffect()

  // Moving the mouse horizontally (X offset) should rotate around the Y axis.
  rotationY = normalizedOffset.x * 10f * intensity

  // Moving the mouse vertically (Y offset) should rotate around the X axis.
  // We negate it so that moving the mouse down tilts the card up.
  rotationX = -normalizedOffset.y * 10f * intensity

  rotationZ = -normalizedOffset.x * 3f * intensity
  scaleX = 1f - .1f * intensity
  scaleY = 1f - .1f * intensity
}

@Composable
internal fun rememberFoilInteractionController(): FoilInteractionController {
  val scope = rememberCoroutineScope()
  return remember { FoilInteractionController(scope) }
}

internal class FoilInteractionController(private val scope: CoroutineScope) {
  companion object {
    private val TRANSITION_SPEC = tween<Float>(300, easing = FastOutSlowInEasing)
    private val CYCLE_SPEC = tween<Float>(2000, easing = FastOutSlowInEasing)
    private val ANIM_START_OFFSET = Offset(-1f, 0.4f)
    private val ANIM_END_OFFSET = Offset(1f, -0.4f)
  }

  val animatableX = Animatable(0f)
  val animatableY = Animatable(0f)

  var mode by mutableStateOf(FoilShaderMode.Tracking)
    private set

  var isHovered by mutableStateOf(false)
    private set

  private var currentCursorPosition by mutableStateOf(Offset.Zero)
  private var animationJob: Job? = null

  fun onHoverStateChanged(isHovered: Boolean) {
    this.isHovered = isHovered
    if (mode == FoilShaderMode.Animating) {
      if (!isHovered) animationJob?.cancel()
      return
    }

    // If in tracking mode, either snap or animate to center.
    if (isHovered) {
      trackCursor()
    }
    else {
      returnToCenter()
    }
  }

  fun onCursorMove(newOffset: Offset) {
    currentCursorPosition = newOffset
    if (mode == FoilShaderMode.Tracking && isHovered) {
      trackCursor()
    }
  }

  fun onClick() {
    if (mode == FoilShaderMode.Animating) return

    animationJob?.cancel()
    animationJob =
      scope.launch {
        try {
          mode = FoilShaderMode.Animating
          // Animate the current image position to the start position of the animation
          animateOffsetTo(ANIM_START_OFFSET, TRANSITION_SPEC)

          // Do the animation
          animateOffsetTo(ANIM_END_OFFSET, CYCLE_SPEC)
          animateOffsetTo(ANIM_START_OFFSET, CYCLE_SPEC)

          // Animate the image to face the cursor current position
          animateOffsetTo(currentCursorPosition, TRANSITION_SPEC)
        }
        finally {
          mode = FoilShaderMode.Tracking
          // After finishing, ensure we're correctly tracking if still hovered.
          if (isHovered) trackCursor()
        }
      }
  }

  private fun trackCursor() {
    scope.launch {
      animatableX.stop()
      animatableY.stop()
      animatableX.snapTo(currentCursorPosition.x)
      animatableY.snapTo(currentCursorPosition.y)
    }
  }

  private fun returnToCenter() {
    scope.launch { animateOffsetTo(Offset.Zero, TRANSITION_SPEC) }
  }

  private suspend fun animateOffsetTo(target: Offset, animationSpec: AnimationSpec<Float>) {
    coroutineScope {
      launch { animatableX.animateTo(target.x, animationSpec) }
      launch { animatableY.animateTo(target.y, animationSpec) }
    }
  }
}

internal enum class FoilShaderMode {
  Tracking,
  Animating,
}
