// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.drag

import java.awt.Dimension
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JDialog

/**
 * An interface that must be implemented by the dialog if it needs to support image changing on the fly.
 */
internal interface DialogWithImage {
  var image: Image?
}

/**
 * An implementation of a dragged image using an undecorated dialog.
 */
internal class DialogDragImageView(private val dialog: JDialog) : DragImageView {
  override var location: Point
    get() = dialog.location
    set(value) {
      val originalDialogSize = preferredSize
      val newDialogScreenBounds = Rectangle(value, originalDialogSize)
      dialog.bounds = newDialogScreenBounds
      // Verify that the bounds are still correct. When moving the dialog across two screens with different DPI scale factors in Windows, the
      // dialog might get resized or relocated, which is at best undesirable, and at worst incorrect (and possibly a bug in the JBR).
      // When the dialog gets more than half of its width into the next screen, Windows considers it to belong to the next screen (although
      // it doesn't yet update the graphicsConfiguration property), and resizes it. It tries to maintain the same physical size, based on DPI.
      // A screen with a 100% scaling factor will be 96 DPI, but a 150% screen will have a DPI of 144. Moving from a 150% screen to a 100%
      // screen will convert a size of 144 to 96, meaning the dialog will shrink. Moving in the opposite direction will cause the dialog to
      // grow.
      // Unfortunately, resizing the dialog will also change the width and move the dialog back to the original screen, but the size is not
      // reset. Continuing the drag will soon put half of the dialog back into the next screen, and the dialog is resized again. This
      // continues until the dialog is tiny. Going in the opposite direction will cause the dialog to repeatedly grow huge.
      // Fortunately, we want the drag image to be the same size relative to the UI, so it's the same "pixel" size regardless of DPI. We can
      // simply reset the size to the same value, and we avoid any problems. There is still a visual step as the DPI changes - the pixel
      // values are the same, but the DPIs are different. This is normal behaviour for Windows, and can be seen with e.g. Notepad.
      // Windows will also sometimes relocate the dialog, but this appears to be incorrect behaviour, possibly a bug in the JBR. After moving
      // halfway into the next screen, the dialog can sometimes (and reproducibly) relocate to an incorrect location, as though the
      // calculation to convert from device to screen coordinates is incredibly wrong (e.g. a 2880x1800@150% screen should be positioned at
      // x=1842 based on screen coordinates of the first screen, or x=2763 based on screen coordinates of the second screen, but instead is
      // shown at x=3919). Continue dragging, and it bounces between the correct location and similar incorrect locations until the dialog is
      // approximately 3/4 of the way into the next screen. Perhaps this is related to the graphicsConfiguration property not being updated
      // correctly. Is the JBR confused about what scaling factors to apply?
      // TODO: Investigate why the JBR is positioning the dialog like this
      if (dialog.bounds != newDialogScreenBounds) {
        dialog.size = originalDialogSize
      }
    }

  override val bounds: Rectangle
    get() = dialog.bounds

  override val size: Dimension
    get() = dialog.size

  override val preferredSize: Dimension
    get() = dialog.preferredSize

  override var image: Image?
    get() = (dialog as? DialogWithImage)?.image
    set(value) {
      (dialog as? DialogWithImage)?.image = value
    }

  override fun show() {
    dialog.isVisible = true
  }

  override fun hide() {
    dialog.dispose()
  }
}
