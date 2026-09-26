// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.wrapIncompatibleThreadStateException
import com.intellij.debugger.ui.tree.render.IconObjectRenderer
import com.intellij.debugger.ui.tree.render.ImageObjectRenderer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.icons.HiDPIImage
import com.intellij.util.ui.JBImageIcon
import com.sun.jdi.ClassType
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.ImageIcon

internal const val IMAGE_DEBUG_SUPPORT_FQN: String = "com.intellij.ide.debug.ImageDebugUtil"
internal const val IMAGE_TO_BYTES_METHOD: String = "imageToBytes"
internal const val ICON_TO_BYTES_METHOD: String = "iconToBytes"
internal const val ICON_TO_BYTES_PREVIEW_METHOD: String = "iconToBytesPreview"
private const val IMAGE_FORMAT_VERSION = 1
private val LOG = logger<HiDpiImageObjectRenderer>()
private val unsupportedFormatLogged = AtomicBoolean(false)

private fun isIJBasedProject(project: Project): Boolean {
  return IntelliJProjectUtil.isIntelliJPlatformProject(project) || IntelliJProjectUtil.isIntelliJPluginProject(project)
}

internal data class DebugImageData(
  val logicalWidth: Int,
  val logicalHeight: Int,
  val pngBytes: ByteArray,
) {
  fun toBufferedImage(): BufferedImage? = ImageIO.read(ByteArrayInputStream(pngBytes))
}

internal fun decodeDebugImageData(data: String?): DebugImageData? {
  if (data == null) return null
  return decodeDebugImageData(data.toByteArray(StandardCharsets.ISO_8859_1))
}

internal fun createHiDpiPreviewIcon(data: DebugImageData): ImageIcon? {
  val raw = data.toBufferedImage() ?: return null
  if (raw.width == data.logicalWidth && raw.height == data.logicalHeight) {
    return JBImageIcon(raw)
  }
  val image = HiDPIImage(raw, data.logicalWidth, data.logicalHeight, BufferedImage.TYPE_INT_ARGB)
  return JBImageIcon(image)
}

private fun getDebugImageData(
  evaluationContext: EvaluationContextImpl,
  obj: Value,
  methodName: String,
  additionalArguments: List<Value> = emptyList(),
): DebugImageData? {
  val data = invokeImageDebugUtilMethod(evaluationContext, obj, methodName, additionalArguments) ?: return null
  return decodeDebugImageData(data)
}

private fun decodeDebugImageData(data: ByteArray): DebugImageData? {
  return try {
    DataInputStream(ByteArrayInputStream(data)).use {
      val formatVersion = it.readUnsignedByte()
      if (formatVersion != IMAGE_FORMAT_VERSION) {
        if (unsupportedFormatLogged.compareAndSet(false, true)) {
          LOG.info(
            "Unsupported HiDPI image format version $formatVersion, expected $IMAGE_FORMAT_VERSION; " +
            "falling back to default image/icon renderers"
          )
        }
        return null
      }
      val logicalWidth = it.readInt()
      val logicalHeight = it.readInt()
      val pngLength = it.readInt()
      if (logicalWidth <= 0 || logicalHeight <= 0 || pngLength < 0) {
        return null
      }
      val pngBytes = ByteArray(pngLength)
      it.readFully(pngBytes)
      DebugImageData(logicalWidth, logicalHeight, pngBytes)
    }
  }
  catch (e: Exception) {
    DebuggerUtilsImpl.logError("Exception while decoding HiDPI image data", e)
    null
  }
}

private fun invokeImageDebugUtilMethod(
  evaluationContext: EvaluationContextImpl,
  obj: Value,
  methodName: String,
  additionalArguments: List<Value> = emptyList(),
): ByteArray? {
  return try {
    wrapIncompatibleThreadStateException {
      val suspendContext = evaluationContext.suspendContext
      val debugProcess = evaluationContext.debugProcess
      if (!debugProcess.isEvaluationPossible(suspendContext)) return null

      val helperClass = findClassOrNull(suspendContext, IMAGE_DEBUG_SUPPORT_FQN) as? ClassType ?: return null
      val arguments = listOf(obj) + additionalArguments
      val bytes = evaluationContext.computeAndKeep {
        DebuggerUtilsImpl.invokeClassMethod(evaluationContext, helperClass, methodName, null, arguments) as? StringReference
      }
      bytes?.value()?.toByteArray(StandardCharsets.ISO_8859_1)
    }
  }
  catch (e: Exception) {
    DebuggerUtilsImpl.logError("Exception while getting HiDPI image data", e)
    null
  }
}

internal class HiDpiImageObjectRenderer : ImageObjectRenderer() {
  override fun getName(): String = "JBHiDPIScaledImage"

  override fun getClassName(): String = "com.intellij.util.JBHiDPIScaledImage"

  override fun isApplicable(project: Project): Boolean {
    return super.isApplicable(project) && isIJBasedProject(project)
  }

  override fun getImageBytes(evaluationContext: EvaluationContextImpl, obj: Value): ByteArray? {
    //TODO: we do not support HiDPI resolution yet, this has to be supported in ShowImagePopupUtil first
    return getDebugImageData(evaluationContext, obj, IMAGE_TO_BYTES_METHOD)?.pngBytes ?: super.getImageBytes(evaluationContext, obj)
  }
}

/**
 * Most IDE icons are exposed in the debuggee as platform-specific wrappers such as CachedImageIcon rather than JBImageIcon,
 * so we need a dedicated renderer for the whole Icon hierarchy to use the HiDPI-aware helper path before the base renderer.
 */
internal class HiDpiIconObjectRenderer : IconObjectRenderer() {
  override fun getName(): String = "HiDPI Icon"

  override fun getClassName(): String = "javax.swing.Icon"

  override fun isApplicable(project: Project): Boolean {
    return super.isApplicable(project) && isIJBasedProject(project)
  }

  override fun getImageBytes(evaluationContext: EvaluationContextImpl, obj: Value): ByteArray? {
    //TODO: we do not support HiDPI resolution yet, this has to be supported in ShowImagePopupUtil first
    return getDebugImageData(evaluationContext, obj, ICON_TO_BYTES_METHOD)?.pngBytes ?: super.getImageBytes(evaluationContext, obj)
  }

  override fun getPreviewIcon(evaluationContext: EvaluationContextImpl, obj: Value): ImageIcon? {
    val maxSize = maxOf(com.intellij.icons.AllIcons.Debugger.Value.iconWidth, com.intellij.icons.AllIcons.Debugger.Value.iconHeight)
    return getHiDpiImageIcon(evaluationContext, obj, maxSize) ?: super.getPreviewIcon(evaluationContext, obj)
  }

  private fun getHiDpiImageIcon(evaluationContext: EvaluationContextImpl, obj: Value, maxSize: Int): ImageIcon? {
    val data = getDebugImageData(
      evaluationContext,
      obj,
      ICON_TO_BYTES_PREVIEW_METHOD,
      listOf(evaluationContext.virtualMachineProxy.mirrorOf(maxSize)),
    )
    if (data == null) return null
    return createHiDpiPreviewIcon(data)
  }
}
