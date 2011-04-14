/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.dom.drawable;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDrawableDomUtil {
  public static final Map<String, String> SPECIAL_STYLEABLE_NAMES = new HashMap<String, String>();
  private static final String[] POSSIBLE_DRAWABLE_ROOTS =
    new String[]{"selector", "bitmap", "nine-patch", "layer-list", "level-list", "transition", "inset", "clip", "scale", "shape"};

  static {
    SPECIAL_STYLEABLE_NAMES.put("selector", "StateListDrawable");
    SPECIAL_STYLEABLE_NAMES.put("bitmap", "BitmapDrawable");
    SPECIAL_STYLEABLE_NAMES.put("nine-patch", "NinePatchDrawable");
    SPECIAL_STYLEABLE_NAMES.put("layer-list", "LayerDrawable");
    SPECIAL_STYLEABLE_NAMES.put("inset", "InsetDrawable");
    SPECIAL_STYLEABLE_NAMES.put("clip", "ClipDrawable");
    SPECIAL_STYLEABLE_NAMES.put("scale", "ScaleDrawable");

    SPECIAL_STYLEABLE_NAMES.put("shape", "GradientDrawable");
    SPECIAL_STYLEABLE_NAMES.put("corners", "DrawableCorners");
    SPECIAL_STYLEABLE_NAMES.put("gradient", "GradientDrawableGradient");
    SPECIAL_STYLEABLE_NAMES.put("padding", "GradientDrawablePadding");
    SPECIAL_STYLEABLE_NAMES.put("size", "GradientDrawableSize");
    SPECIAL_STYLEABLE_NAMES.put("solid", "GradientDrawableSolid");
    SPECIAL_STYLEABLE_NAMES.put("stroke", "GradientDrawableStroke");
  }

  private AndroidDrawableDomUtil() {
  }

  public static boolean isDrawableResourceFile(@NotNull XmlFile file) {
    return AndroidResourceDomFileDescription.doIsMyFile(file, new String[]{"drawable"});
  }

  public static List<String> getPossibleRoots() {
    return Arrays.asList(POSSIBLE_DRAWABLE_ROOTS);
  }
}
