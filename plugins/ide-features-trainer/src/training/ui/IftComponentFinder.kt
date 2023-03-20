// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import org.assertj.core.api.Assertions
import org.assertj.core.util.Strings
import org.assertj.swing.core.*
import org.assertj.swing.exception.ComponentLookupException
import org.assertj.swing.format.Formatting
import org.assertj.swing.hierarchy.ComponentHierarchy
import org.assertj.swing.hierarchy.SingleComponentHierarchy
import java.awt.Component
import java.awt.Container
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import javax.swing.JLabel

/**
 * This implementation is replacement for [org.assertj.swing.core.BasicComponentFinder].
 * Basic finder sends many requests to the EDT (two requests for each UI component) and in result search performs very long in some cases.
 * This finder sends only one EDT request for each UI root. And this method looks much faster.
 */
internal class IftComponentFinder(private val basicFinder: ComponentFinder,
                                  private val hierarchy: ComponentHierarchy,
                                  private val settings: Settings) : ComponentFinder {
  override fun findAll(matcher: ComponentMatcher): Collection<Component> {
    return doFindAll(hierarchy, matcher)
  }

  override fun findAll(root: Container, matcher: ComponentMatcher): Collection<Component> {
    return doFindAll(hierarchy(root), matcher)
  }

  override fun <T : Component> findAll(matcher: GenericTypeMatcher<T>): Collection<T> {
    return doFindAll(hierarchy, matcher).map { matcher.supportedType().cast(it) }
  }

  override fun <T : Component> findAll(root: Container, matcher: GenericTypeMatcher<T>): Collection<T> {
    return doFindAll(hierarchy(root), matcher).map { matcher.supportedType().cast(it) }
  }

  private fun find(hierarchy: ComponentHierarchy, matcher: ComponentMatcher): Component {
    val found = doFindAll(hierarchy, matcher)
    if (found.isEmpty()) {
      throw componentNotFound(hierarchy, matcher)
    }
    if (found.size > 1) {
      throw multipleComponentsFound(found, matcher)
    }
    return found.first()
  }

  private fun doFindAll(hierarchy: ComponentHierarchy, matcher: ComponentMatcher): Collection<Component> {
    val found: MutableSet<Component> = LinkedHashSet()
    for (c in rootsOf(hierarchy)) {
      doFind(hierarchy, matcher, c, found)
    }
    return found
  }

  private fun doFind(hierarchy: ComponentHierarchy, matcher: ComponentMatcher, root: Component, found: MutableSet<Component>) {
    val allComponents: MutableSet<Component> = LinkedHashSet()
    collectAll(hierarchy, root, allComponents)
    isMatching(allComponents, matcher, found)
  }

  private fun collectAll(hierarchy: ComponentHierarchy, root: Component, allComponents: MutableSet<Component>) {
    for (c in hierarchy.childrenOf(root)) {
      if (c == null) continue
      collectAll(hierarchy, c, allComponents)
    }
    allComponents.add(root)
  }

  private fun isMatching(allComponents: Set<Component>, matcher: ComponentMatcher, found: MutableSet<Component>) {
    invokeAndWaitIfNeeded(ModalityState.any()) {
      for (component in allComponents) {
        if (matcher.matches(component)) {
          found.add(component)
        }
      }
    }
  }

  private fun rootsOf(hierarchy: ComponentHierarchy): Collection<Component> {
    return invokeAndWaitIfNeeded(ModalityState.any()) { hierarchy.roots().filterNotNull() }
  }

  override fun printer(): ComponentPrinter {
    return basicFinder.printer()
  }

  override fun includeHierarchyIfComponentNotFound(): Boolean {
    return basicFinder.includeHierarchyIfComponentNotFound()
  }

  override fun includeHierarchyIfComponentNotFound(newValue: Boolean) {
    basicFinder.includeHierarchyIfComponentNotFound(newValue)
  }

  //-----------------------------------------------------------------------------------------------
  /**   Copy pasted logic from [org.assertj.swing.core.BasicComponentFinder]                     **/
  //-----------------------------------------------------------------------------------------------

  override fun <T : Component> findByType(type: Class<T>): T {
    return findByType(type, requireShowing())
  }

  override fun <T : Component> findByType(type: Class<T>, showing: Boolean): T {
    return type.cast(find(TypeMatcher(type, showing)))
  }

  override fun <T : Component> findByType(root: Container, type: Class<T>): T {
    return findByType(root, type, requireShowing())
  }

  override fun <T : Component> findByType(root: Container, type: Class<T>, showing: Boolean): T {
    return type.cast(find(root, TypeMatcher(type, showing)))
  }

  override fun <T : Component> findByName(name: String?, type: Class<T>): T {
    return findByName(name, type, requireShowing())
  }

  override fun <T : Component> findByName(name: String?, type: Class<T>, showing: Boolean): T {
    val found = find(NameMatcher(name, type, showing))
    return type.cast(found)
  }

  override fun findByName(name: String?): Component {
    return findByName(name, requireShowing())
  }

  override fun findByName(name: String?, showing: Boolean): Component {
    return find(NameMatcher(name, showing))
  }

  override fun <T : Component> findByLabel(label: String?, type: Class<T>): T {
    return findByLabel(label, type, requireShowing())
  }

  override fun <T : Component> findByLabel(label: String?, type: Class<T>, showing: Boolean): T {
    val found = find(LabelMatcher(label, type, showing))
    return labelFor(found, type)
  }

  override fun findByLabel(label: String?): Component {
    return findByLabel(label, requireShowing())
  }

  override fun findByLabel(label: String?, showing: Boolean): Component {
    val found = find(LabelMatcher(label, showing))
    return labelFor(found, Component::class.java)
  }

  override fun <T : Component> find(m: GenericTypeMatcher<T>): T {
    val found = find(m as ComponentMatcher)
    return m.supportedType().cast(found)
  }

  override fun find(m: ComponentMatcher): Component {
    return find(hierarchy, m)
  }

  override fun <T : Component> findByName(root: Container, name: String?, type: Class<T>): T {
    return findByName(root, name, type, requireShowing())
  }

  override fun <T : Component> findByName(root: Container, name: String?, type: Class<T>, showing: Boolean): T {
    val found = find(root, NameMatcher(name, type, showing))
    return type.cast(found)
  }

  override fun findByName(root: Container, name: String?): Component {
    return findByName(root, name, requireShowing())
  }

  override fun findByName(root: Container, name: String?, showing: Boolean): Component {
    return find(root, NameMatcher(name, showing))
  }

  override fun <T : Component> findByLabel(root: Container, label: String?, type: Class<T>): T {
    return findByLabel(root, label, type, requireShowing())
  }

  override fun <T : Component> findByLabel(root: Container, label: String?, type: Class<T>, showing: Boolean): T {
    val found = find(root, LabelMatcher(label, type, showing))
    return labelFor(found, type)
  }

  override fun findByLabel(root: Container, label: String?): Component {
    return findByLabel(root, label, requireShowing())
  }

  private fun requireShowing(): Boolean {
    return settings.componentLookupScope().requireShowing()
  }

  override fun findByLabel(root: Container, label: String?, showing: Boolean): Component {
    val found = find(root, LabelMatcher(label, showing))
    return labelFor(found, Component::class.java)
  }


  private fun <T> labelFor(label: Component, type: Class<T>): T {
    Assertions.assertThat(label).isInstanceOf(JLabel::class.java)
    val target = (label as JLabel).labelFor
    Assertions.assertThat(target).isInstanceOf(type)
    return type.cast(target)
  }

  override fun <T : Component> find(root: Container, m: GenericTypeMatcher<T>): T {
    val found = find(root, m as ComponentMatcher)
    return m.supportedType().cast(found)
  }

  override fun find(root: Container?, m: ComponentMatcher): Component {
    return find(hierarchy(root), m)
  }

  private fun componentNotFound(h: ComponentHierarchy, m: ComponentMatcher): ComponentLookupException {
    var message = Strings.concat("Unable to find component using matcher ", m, ".")
    if (includeHierarchyIfComponentNotFound()) {
      message = Strings.concat(message, System.lineSeparator(), System.lineSeparator(), "Component hierarchy:", System.lineSeparator(),
                               formattedHierarchy(root(h)))
    }
    throw ComponentLookupException(message)
  }

  private fun root(h: ComponentHierarchy?): Container? {
    return if (h is SingleComponentHierarchy) {
      h.root()
    }
    else null
  }

  private fun formattedHierarchy(root: Container?): String {
    val out = ByteArrayOutputStream()
    val printStream = PrintStream(out, true)
    printer().printComponents(printStream, root)
    printStream.flush()
    return String(out.toByteArray(), StandardCharsets.UTF_8)
  }

  private fun multipleComponentsFound(found: Collection<Component>, m: ComponentMatcher): ComponentLookupException {
    val message = StringBuilder()
    val format = "Found more than one component using matcher %s. %n%nFound:"
    message.append(String.format(format, m.toString()))
    appendComponents(message, found)
    if (!found.isEmpty()) {
      message.append(System.lineSeparator())
    }
    throw ComponentLookupException(message.toString(), found)
  }

  private fun appendComponents(message: StringBuilder, found: Collection<Component>) {
    invokeAndWaitIfNeeded(ModalityState.any()) {
      for (c in found) {
        message.append(String.format("%n%s", Formatting.format(c)))
      }
    }
  }

  private fun hierarchy(root: Container?): ComponentHierarchy {
    return root?.let { SingleComponentHierarchy(it, hierarchy) } ?: hierarchy
  }
}