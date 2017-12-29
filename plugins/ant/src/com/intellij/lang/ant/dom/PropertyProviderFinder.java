/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntSupport;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public abstract class PropertyProviderFinder extends AntDomRecursiveVisitor {

  protected static <K, V> void cacheResult(@Nullable final DomElement context, final Key<Map<K, V>> cacheKind, K key, V value) {
    if (context != null) {
      Map<K, V> cachemap = cacheKind.get(context);
      if (cachemap == null) {
        cacheKind.set(context, cachemap = Collections.synchronizedMap(new HashMap<K, V>()));
      }
      cachemap.put(key, value);
    }
  }

  public enum Stage {
    RESOLVE_MAP_BUILDING_STAGE, TARGETS_WALKUP_STAGE
  }
  private Stage myStage = Stage.RESOLVE_MAP_BUILDING_STAGE;

  private final Stack<String> myCurrentTargetEffectiveName = new Stack<>();

  private final AntDomElement myContextElement;
  private boolean myStopped;
  private final TargetsNameContext myNameContext = new TargetsNameContext();
  private final Map<String, AntDomTarget> myTargetsResolveMap = new HashMap<>(); // target effective name -> ant target
  private final Map<String, List<String>> myDependenciesMap = new HashMap<>();   // target effective name -> dependencies effective names

  private final Set<String> myProcessedTargets = new HashSet<>();
  private final Set<AntDomProject> myVisitedProjects = new HashSet<>();

  protected PropertyProviderFinder(DomElement contextElement) {
    myContextElement = contextElement != null? contextElement.getParentOfType(AntDomElement.class, false) : null;
  }

  public void execute(AntDomProject startProject, String initialTargetName) {
    myStage = Stage.RESOLVE_MAP_BUILDING_STAGE;
    startProject.accept(this);
    stageCompleted(Stage.RESOLVE_MAP_BUILDING_STAGE, Stage.TARGETS_WALKUP_STAGE);
    if (!myStopped) {
      myStage = Stage.TARGETS_WALKUP_STAGE;
      final AntDomTarget target = initialTargetName != null? getTargetByName(initialTargetName) : null;
      if (target != null) {
        processTarget(initialTargetName, target);
      }
      List<String> unprocessed = null;
      for (String s : myTargetsResolveMap.keySet()) {
        if (!myProcessedTargets.contains(s)) {
          if (unprocessed == null) {
            unprocessed = new ArrayList<>();
          }
          unprocessed.add(s);
        }
      }
      if (unprocessed != null) {
        for (String targetName : unprocessed) {
          processTarget(targetName, myTargetsResolveMap.get(targetName));
        }
      }
    }
  }

  private void processTarget(String targetEffectiveName, AntDomTarget target) {
    myCurrentTargetEffectiveName.push(targetEffectiveName);
    try {
      target.accept(this);
    }
    finally {
      myCurrentTargetEffectiveName.pop();
    }
  }

  public void visitTarget(AntDomTarget target) {
    if (myStage == Stage.TARGETS_WALKUP_STAGE) {
      final String targetEffectiveName = myCurrentTargetEffectiveName.peek();
      if (myProcessedTargets.add(targetEffectiveName)) {
        final List<String> depsList = myDependenciesMap.get(targetEffectiveName);
        if (depsList != null) {
          for (String dependencyName : depsList) {
            final AntDomTarget dependency = getTargetByName(dependencyName);
            if (dependency != null) {
              processTarget(dependencyName, dependency);
            }
          }
        }
        super.visitTarget(target);
      }
    }
    else if (myStage == Stage.RESOLVE_MAP_BUILDING_STAGE){
      final String declaredTargetName = target.getName().getRawText();
      String effectiveTargetName = null;
      final InclusionKind inclusionKind = myNameContext.getCurrentInclusionKind();
      switch (inclusionKind) {
        case IMPORT:
          final String alias = myNameContext.getShortPrefix() + declaredTargetName;
          if (!myTargetsResolveMap.containsKey(declaredTargetName)) {
            effectiveTargetName = declaredTargetName;
            myTargetsResolveMap.put(alias, target); 
          }
          else {
            effectiveTargetName = alias;
          }
          break;

        case INCLUDE:
          effectiveTargetName = myNameContext.getFQPrefix() + declaredTargetName;
          break;

        default:
          effectiveTargetName = declaredTargetName;
          break;
      }
      if (effectiveTargetName != null) {
        final AntDomTarget existingTarget = myTargetsResolveMap.get(effectiveTargetName);
        if (existingTarget != null && Comparing.equal(existingTarget.getAntProject(), target.getAntProject())) {
          duplicateTargetFound(existingTarget, target, effectiveTargetName);
        }
        else {
          myTargetsResolveMap.put(effectiveTargetName, target);
          final String dependsStr = target.getDependsList().getRawText();
          Map<String, Pair<AntDomTarget, String>> depsMap = Collections.emptyMap();
          if (dependsStr != null) {
            depsMap = new HashMap<>();
            final StringTokenizer tokenizer = new StringTokenizer(dependsStr, ",", false);
            while (tokenizer.hasMoreTokens()) {
              final String token = tokenizer.nextToken().trim();
              final String dependentTargetEffectiveName = myNameContext.calcTargetReferenceText(token);
              final AntDomTarget dependent = getTargetByName(dependentTargetEffectiveName);
              if (dependent != null) {
                depsMap.put(token, Pair.create(dependent, dependentTargetEffectiveName));
              }
              addDependency(effectiveTargetName, dependentTargetEffectiveName);
            }
          }
          targetDefined(target, effectiveTargetName, depsMap);
        }
      }
    }
  }

  @Override
  public void visitAntDomElement(AntDomElement element) {
    if (myStopped) {
      return; 
    }
    if (element.equals(myContextElement)) {
      stop();
    }
    else {
      if (element instanceof PropertiesProvider) {
        propertyProviderFound(((PropertiesProvider)element));
      }
    }
    if (!myStopped) {
      //super.visitAntDomElement(element);
      for (Iterator<AntDomElement> iterator = element.getAntChildrenIterator(); iterator.hasNext();) {
        AntDomElement child = iterator.next();
        child.accept(this);
        if (myStage == Stage.TARGETS_WALKUP_STAGE) {
          if (myStopped) {
            break;
          }
        }
      }
    }
  }

  @Nullable
  protected AntDomTarget getTargetByName(String effectiveName) {
    return myTargetsResolveMap.get(effectiveName);
  }

  @NotNull
  public final Map<String, AntDomTarget> getDiscoveredTargets() {
    return Collections.unmodifiableMap(myTargetsResolveMap);
  }

  public AntDomElement getContextElement() {
    return myContextElement;
  }

  protected void stop() {
    myStopped = true;
  }

  /**
   * @param propertiesProvider
   * @return true if search should be continued and false in order to stop
   */
  protected abstract void propertyProviderFound(PropertiesProvider propertiesProvider);


  public void visitInclude(AntDomInclude includeTag) {
    processFileInclusion(includeTag, InclusionKind.INCLUDE);
  }

  public void visitImport(AntDomImport importTag) {
    processFileInclusion(importTag, InclusionKind.IMPORT);
  }

  public void visitProject(AntDomProject project) {
    if (myVisitedProjects.add(project)) {
      try {
        super.visitProject(project);
      }
      finally {
        myVisitedProjects.remove(project);
      }
    }
  }

  private void processFileInclusion(AntDomIncludingDirective directive, final InclusionKind kind) {
    if (directive.equals(myContextElement)) {
      stop();
    }
    if (myStopped) {
      return;
    }
    final PsiFileSystemItem item = directive.getFile().getValue();
    if (item instanceof PsiFile) {
      final AntDomProject slaveProject = item instanceof XmlFile ? AntSupport.getAntDomProjectForceAntFile((XmlFile)item) : null;
      if (slaveProject != null) {
        myNameContext.pushPrefix(directive, kind, slaveProject);
        try {
          slaveProject.accept(this);
        }
        finally {
          myNameContext.popPrefix();
        }
      }
    }
  }

  private void addDependency(String effectiveTargetName, String dependentTargetEffectiveName) {
    List<String> list = myDependenciesMap.get(effectiveTargetName);
    if (list == null) {
      myDependenciesMap.put(effectiveTargetName, list = new ArrayList<>());
    }
    list.add(dependentTargetEffectiveName);
  }

  /**
   * @param target
   * @param taregetEffectiveName
   * @param dependenciesMap Map declared dependency reference->pair[tareget object, effective reference name]
   */
  protected void targetDefined(AntDomTarget target, String taregetEffectiveName, Map<String, Pair<AntDomTarget, String>> dependenciesMap) {
  }

  /**
   * @param existingTarget
   * @param duplicatingTarget
   * @param taregetEffectiveName
   */
  protected void duplicateTargetFound(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String taregetEffectiveName) {
  }

  protected void stageCompleted(Stage completedStage, Stage startingStage) {
  }

  private static enum InclusionKind {
    INCLUDE("included"), IMPORT("imported"), TOPLEVEL("toplevel");

    private final String myDisplayName;

    private InclusionKind(String displayName) {
      myDisplayName = displayName;
    }

    public String toString() {
      return myDisplayName;
    }
  }

  private static class TargetsNameContext {
    private int myDefaultPrefixCounter = 0;
    private final LinkedList<Pair<String, InclusionKind>> myPrefixes = new LinkedList<>();
    private String myCurrentPrefix = null;

    public String calcTargetReferenceText(String targetReferenceText) {
      if (!myPrefixes.isEmpty()) {
        final InclusionKind kind = myPrefixes.getLast().getSecond();
        switch (kind) {
          case IMPORT  : return targetReferenceText;
          case INCLUDE : return getFQPrefix() + targetReferenceText;
        }
      }
      return targetReferenceText;
    }

    @NotNull
    public InclusionKind getCurrentInclusionKind() {
      if (myPrefixes.isEmpty()) {
        return InclusionKind.TOPLEVEL;
      }
      return myPrefixes.getLast().getSecond();
    }

    @NotNull
    public String getFQPrefix() {
      if (myCurrentPrefix != null) {
        return myCurrentPrefix;
      }
      if (myPrefixes.isEmpty()) {
        return "";
      }
      StringBuffer buf = new StringBuffer();
      for (Pair<String, InclusionKind> prefix : myPrefixes) {
        buf.append(prefix.getFirst());
      }
      return myCurrentPrefix = buf.toString();
    }

    @NotNull
    public String getShortPrefix() {
      return myPrefixes.isEmpty()? "" : myPrefixes.getLast().getFirst();
    }

    public void pushPrefix(AntDomIncludingDirective directive, final InclusionKind kind, final @NotNull AntDomProject slaveProject) {
      final String separator = directive.getTargetPrefixSeparatorValue();
      String prefix = directive.getTargetPrefix().getStringValue();
      if (prefix == null) {
        prefix = slaveProject.getName().getRawText();
        if (prefix == null) {
          prefix = "anonymous" + (myDefaultPrefixCounter++);
        }
      }
      pushPrefix(prefix.endsWith(separator) ? prefix : prefix + separator, kind);
    }

    public void pushPrefix(String prefix, InclusionKind kind) {
      myCurrentPrefix = null;
      myPrefixes.addLast(Pair.create(prefix, kind));
    }

    public void popPrefix() {
      myCurrentPrefix = null;
      myPrefixes.removeLast();
    }
  }
}
