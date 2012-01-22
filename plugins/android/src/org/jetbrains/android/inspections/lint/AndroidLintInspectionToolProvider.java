package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.checks.*;
import com.intellij.codeInspection.InspectionToolProvider;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLintInspectionToolProvider implements InspectionToolProvider {
  @Override
  public Class[] getInspectionClasses() {
    return new Class[] {
      AndroidLintContentDescriptionInspection.class,
      AndroidLintAdapterViewChildrenInspection.class,
      AndroidLintScrollViewCountInspection.class,
      AndroidLintDeprecatedInspection.class,
      AndroidLintMissingPrefixInspection.class,
      AndroidLintDuplicateIdsInspection.class,
      AndroidLintGridLayoutInspection.class,
      AndroidLintHardcodedTextInspection.class,
      AndroidLintInefficientWeightInspection.class,
      AndroidLintNestedWeightsInspection.class,
      AndroidLintDisableBaselineAlignmentInspection.class,
      AndroidLintManifestOrderInspection.class,
      AndroidLintMergeRootFrameInspection.class,
      AndroidLintNestedScrollingInspection.class,
      AndroidLintObsoleteLayoutParamInspection.class,
      AndroidLintProguardInspection.class,
      AndroidLintPxUsageInspection.class,
      AndroidLintScrollViewSizeInspection.class,
      AndroidLintExportedServiceInspection.class,
      AndroidLintGrantAllUrisInspection.class,
      AndroidLintStateListReachableInspection.class,
      AndroidLintTextFieldsInspection.class,
      AndroidLintTooManyViewsInspection.class,
      AndroidLintTooDeepLayoutInspection.class,
      AndroidLintTypographyDashesInspection.class,
      AndroidLintTypographyQuotesInspection.class,
      AndroidLintTypographyFractionsInspection.class,
      AndroidLintTypographyEllipsisInspection.class,
      AndroidLintTypographyOtherInspection.class,
      AndroidLintUseCompoundDrawablesInspection.class,
      AndroidLintUselessParentInspection.class,
      AndroidLintUselessLeafInspection.class,

      // batch-mode-only
      AndroidLintInconsistentArraysInspection.class,
      AndroidLintDuplicateIncludedIdsInspection.class,
      AndroidLintIconExpectedSizeInspection.class,
      AndroidLintIconDipSizeInspection.class,
      AndroidLintIconLocationInspection.class,
      AndroidLintIconDensitiesInspection.class,
      AndroidLintIconMissingDensityFolderInspection.class,
      AndroidLintGifUsageInspection.class,
      AndroidLintIconDuplicatesInspection.class,
      AndroidLintIconDuplicatesConfigInspection.class,
      AndroidLintIconNoDpiInspection.class,
      AndroidLintOverdrawInspection.class,
      AndroidLintMissingTranslationInspection.class,
      AndroidLintExtraTranslationInspection.class,
      AndroidLintUnusedResourcesInspection.class,
      AndroidLintUnusedIdsInspection.class
    };
  }

  /**
   * Batch-mode-only inspections
   */
  public static class AndroidLintInconsistentArraysInspection extends AndroidLintInspectionBase {
    public AndroidLintInconsistentArraysInspection() {
      super("Inconsistent array sizes", ArraySizeDetector.INCONSISTENT);
    }
  }

  public static class AndroidLintDuplicateIncludedIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateIncludedIdsInspection() {
      super("Duplicate ids across layouts combined with include tags", DuplicateIdDetector.CROSS_LAYOUT);
    }
  }

  public static class AndroidLintIconExpectedSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintIconExpectedSizeInspection() {
      super("Icon have incorrect size", IconDetector.ICON_EXPECTED_SIZE);
    }
  }

  public static class AndroidLintIconDipSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDipSizeInspection() {
      super("Icon density-independent size validation", IconDetector.ICON_DIP_SIZE);
    }
  }

  public static class AndroidLintIconLocationInspection extends AndroidLintInspectionBase {
    public AndroidLintIconLocationInspection() {
      super("Image defined in density-independent drawable folder", IconDetector.ICON_LOCATION);
    }
  }

  public static class AndroidLintIconDensitiesInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDensitiesInspection() {
      super("Icon densities validation", IconDetector.ICON_DENSITIES);
    }
  }

  public static class AndroidLintIconMissingDensityFolderInspection extends AndroidLintInspectionBase {
    public AndroidLintIconMissingDensityFolderInspection() {
      super("Missing density folder", IconDetector.ICON_MISSING_FOLDER);
    }
  }

  public static class AndroidLintGifUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintGifUsageInspection() {
      super("Using the .gif format for bitmaps is discouraged", IconDetector.GIF_USAGE);
    }
  }

  public static class AndroidLintIconDuplicatesInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDuplicatesInspection() {
      super("Duplicated icons under different names", IconDetector.DUPLICATES_NAMES);
    }
  }

  public static class AndroidLintIconDuplicatesConfigInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDuplicatesConfigInspection() {
      super("Identical bitmaps across various configurations", IconDetector.DUPLICATES_CONFIGURATIONS);
    }
  }

  public static class AndroidLintIconNoDpiInspection extends AndroidLintInspectionBase {
    public AndroidLintIconNoDpiInspection() {
      super("Icon appear in both -nodpi and dpi folders", IconDetector.ICON_NODPI);
    }
  }

  public static class AndroidLintOverdrawInspection extends AndroidLintInspectionBase {
    public AndroidLintOverdrawInspection() {
      super("Overdraw issues", OverdrawDetector.ISSUE);
    }
  }

  public static class AndroidLintMissingTranslationInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingTranslationInspection() {
      super("Incomplete translation", TranslationDetector.MISSING);
    }
  }

  public static class AndroidLintExtraTranslationInspection extends AndroidLintInspectionBase {
    public AndroidLintExtraTranslationInspection() {
      super("Extra translation", TranslationDetector.EXTRA);
    }
  }

  public static class AndroidLintUnusedResourcesInspection extends AndroidLintInspectionBase {
    public AndroidLintUnusedResourcesInspection() {
      super("Unused resources", UnusedResourceDetector.ISSUE);
    }
  }

  public static class AndroidLintUnusedIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintUnusedIdsInspection() {
      super("Unused id's", UnusedResourceDetector.ISSUE_IDS);
    }
  }

  /**
   * Local inspections processed by AndroidLintExternalAnnotator
   */
  public static class AndroidLintContentDescriptionInspection extends AndroidLintInspectionBase {
    public AndroidLintContentDescriptionInspection() {
      super("Missing content description", AccessibilityDetector.ISSUE);
    }
  }

  public static class AndroidLintAdapterViewChildrenInspection extends AndroidLintInspectionBase {
    public AndroidLintAdapterViewChildrenInspection() {
      super("AdapterView element cannot have children", ChildCountDetector.ADAPTERVIEW_ISSUE);
    }
  }

  public static class AndroidLintScrollViewCountInspection extends AndroidLintInspectionBase {
    public AndroidLintScrollViewCountInspection() {
      super("ScrollView element can have only one child", ChildCountDetector.SCROLLVIEW_ISSUE);
    }
  }

  public static class AndroidLintDeprecatedInspection extends AndroidLintInspectionBase {
    public AndroidLintDeprecatedInspection() {
      super("Deprecated XML elements", DeprecationDetector.ISSUE);
    }
  }

  public static class AndroidLintMissingPrefixInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingPrefixInspection() {
      super("Missing android XML namespace", DetectMissingPrefix.MISSING_NAMESPACE);
    }
  }

  public static class AndroidLintDuplicateIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateIdsInspection() {
      super("Duplicate ids within a single layout", DuplicateIdDetector.WITHIN_LAYOUT);
    }
  }

  public static class AndroidLintGridLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintGridLayoutInspection() {
      super("GridLayout validation", GridLayoutDetector.ISSUE);
    }
  }

  public static class AndroidLintHardcodedTextInspection extends AndroidLintInspectionBase {
    public AndroidLintHardcodedTextInspection() {
      super("Hardcoded text", HardcodedValuesDetector.ISSUE);
    }
  }

  public static class AndroidLintInefficientWeightInspection extends AndroidLintInspectionBase {
    public AndroidLintInefficientWeightInspection() {
      super("Inefficient layout weight", InefficientWeightDetector.INEFFICIENT_WEIGHT);
    }
  }

  public static class AndroidLintNestedWeightsInspection extends AndroidLintInspectionBase {
    public AndroidLintNestedWeightsInspection() {
      super("Nested layout weights", InefficientWeightDetector.NESTED_WEIGHTS);
    }
  }

  public static class AndroidLintDisableBaselineAlignmentInspection extends AndroidLintInspectionBase {
    public AndroidLintDisableBaselineAlignmentInspection() {
      super("Missing baselineAligned attribute", InefficientWeightDetector.BASELINE_WEIGHTS);
    }
  }

  public static class AndroidLintManifestOrderInspection extends AndroidLintInspectionBase {
    public AndroidLintManifestOrderInspection() {
      super("Incorrect order of elements in manifest", ManifestOrderDetector.ISSUE);
    }
  }

  public static class AndroidLintMergeRootFrameInspection extends AndroidLintInspectionBase {
    public AndroidLintMergeRootFrameInspection() {
      super("FrameLayout can be replaced with <merge> tag", MergeRootFrameLayoutDetector.ISSUE);
    }
  }

  public static class AndroidLintNestedScrollingInspection extends AndroidLintInspectionBase {
    public AndroidLintNestedScrollingInspection() {
      super("Nested scrolling widgets", NestedScrollingWidgetDetector.ISSUE);
    }
  }

  public static class AndroidLintObsoleteLayoutParamInspection extends AndroidLintInspectionBase {
    public AndroidLintObsoleteLayoutParamInspection() {
      super("Obsolete layout params", ObsoleteLayoutParamsDetector.ISSUE);
    }
  }

  public static class AndroidLintProguardInspection extends AndroidLintInspectionBase {
    public AndroidLintProguardInspection() {
      super("Proguard config file validation", ProguardDetector.ISSUE);
    }
  }

  public static class AndroidLintPxUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintPxUsageInspection() {
      super("Using 'px' dimension", PxUsageDetector.ISSUE);
    }
  }

  public static class AndroidLintScrollViewSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintScrollViewSizeInspection() {
      super("ScrollView size validation", ScrollViewChildDetector.ISSUE);
    }
  }

  public static class AndroidLintExportedServiceInspection extends AndroidLintInspectionBase {
    public AndroidLintExportedServiceInspection() {
      super("Exported service does not require permission", SecurityDetector.EXPORTED_SERVICE);
    }
  }

  public static class AndroidLintGrantAllUrisInspection extends AndroidLintInspectionBase {
    public AndroidLintGrantAllUrisInspection() {
      super("Content provider shares everything", SecurityDetector.OPEN_PROVIDER);
    }
  }

  public static class AndroidLintStateListReachableInspection extends AndroidLintInspectionBase {
    public AndroidLintStateListReachableInspection() {
      super("Unreachable state in a <selector>", StateListDetector.ISSUE);
    }
  }

  public static class AndroidLintTextFieldsInspection extends AndroidLintInspectionBase {
    public AndroidLintTextFieldsInspection() {
      super("Text field missing inputType or hint settings", TextFieldDetector.ISSUE);
    }
  }

  public static class AndroidLintTooManyViewsInspection extends AndroidLintInspectionBase {
    public AndroidLintTooManyViewsInspection() {
      super("Layout has too many views", TooManyViewsDetector.TOO_MANY);
    }
  }

  public static class AndroidLintTooDeepLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintTooDeepLayoutInspection() {
      super("Layout hierarchy is too deep", TooManyViewsDetector.TOO_DEEP);
    }
  }

  public static class AndroidLintTypographyDashesInspection extends AndroidLintInspectionBase {
    public AndroidLintTypographyDashesInspection() {
      super("Hyphen can be replaced by dash", TypographyDetector.DASHES);
    }
  }

  public static class AndroidLintTypographyQuotesInspection extends AndroidLintInspectionBase {
    public AndroidLintTypographyQuotesInspection() {
      super("Straight quotes can be replaced by curvy quotes", TypographyDetector.QUOTES);
    }
  }

  public static class AndroidLintTypographyFractionsInspection extends AndroidLintInspectionBase {
    public AndroidLintTypographyFractionsInspection() {
      super("Fraction string can be replaced with fraction character", TypographyDetector.FRACTIONS);
    }
  }

  public static class AndroidLintTypographyEllipsisInspection extends AndroidLintInspectionBase {
    public AndroidLintTypographyEllipsisInspection() {
      super("Ellipsis string can be replaced with ellipsis character", TypographyDetector.ELLIPSIS);
    }
  }

  public static class AndroidLintTypographyOtherInspection extends AndroidLintInspectionBase {
    public AndroidLintTypographyOtherInspection() {
      super("Other typographical problems", TypographyDetector.OTHER);
    }
  }

  public static class AndroidLintUseCompoundDrawablesInspection extends AndroidLintInspectionBase {
    public AndroidLintUseCompoundDrawablesInspection() {
      super("Node can be replaced by TextView with compound drawables", UseCompoundDrawableDetector.ISSUE);
    }
  }

  public static class AndroidLintUselessParentInspection extends AndroidLintInspectionBase {
    public AndroidLintUselessParentInspection() {
      super("Useless parent layout", UselessViewDetector.USELESS_PARENT);
    }
  }

  public static class AndroidLintUselessLeafInspection extends AndroidLintInspectionBase {
    public AndroidLintUselessLeafInspection() {
      super("Useless leaf layout", UselessViewDetector.USELESS_LEAF);
    }
  }
}
