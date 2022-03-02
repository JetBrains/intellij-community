// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.kdoc

import com.intellij.codeInsight.documentation.AbstractExternalFilter
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.ide.BrowserUtil
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Urls
import com.intellij.util.castSafelyTo
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import org.jetbrains.builtInWebServer.WebServerPathToFileManager
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern


/**
 * A set of utilities for generating external docs urls for given Kotlin elements,
 * which then can be fetched by the [KotlinDocExtractorFromJavaDoc].
 */
object KotlinExternalDocUrlsProvider {

    /**
     * Generates list of possible urls to JavaDocs.html for the given Kotlin [element].
     *
     * Note that different `javadoc` versions may generate docs' html pages with different anchors id format,
     * e.g. placing anchor for method as `<a id='method(Arg1Type,Arg2Type)'/>` or `<a id='method-Arg1Type-Arg2Type-'/>`.
     *
     * So, for example, for the method `Pizza#contains(ingredient: Pizza.Ingredient)` possible urls may be
     * ```kotlin
     * listOf(
     *   "http://localhost:63343/kotlin-use-javadoc-library/test-library-1.0-SNAPSHOT-javadoc.jar/some/pack/Pizza.html#contains(Pizza.Ingredient)",
     *
     *   "http://localhost:63343/kotlin-use-javadoc-library/test-library-1.0-SNAPSHOT-javadoc.jar/some/pack/Pizza.html#contains-Pizza.Ingredient-"
     * )
     * ```
     * where paths lead to our [org.jetbrains.builtInWebServer].
     * Note anchors `contains(Pizza.Ingredient)` and `contains-Pizza.Ingredient-`
     * by which the [fetcher][KotlinDocExtractorFromJavaDoc]
     * is supposed to understand which part of the docs is requested.
     */
    fun getExternalJavaDocUrl(element: PsiElement?): List<String>? {
        val urls: List<String>? = when (element) {
            is KtEnumEntry -> findUrlsForEnumEntry(element)
            is KtClass -> findUrlsForClass(element)
            is KtFunction -> findUrlsForLightElement(element, LightClassUtil.getLightClassMethod(element))
            is KtProperty -> findUrlsForLightProperty(element, LightClassUtil.getLightClassPropertyMethods(element))
            is KtParameter -> findUrlsForLightProperty(element, LightClassUtil.getLightClassPropertyMethods(element))
            // in case when quick doc is requested for Kotlin library element from Java code
            is KtLightClass -> findUrlsForClass(element)
            is KtLightMember<*> -> findUrlsForLightElement(findUrlsForClass(element.containingClass), element)
            else -> null // core API requires null instead of empty list do designate that we do not provide any urls
        }
        return urls?.map { FileUtil.toSystemIndependentName(it) }
    }

    private fun findUrlsForClass(aClass: KtClass?): List<String> {
        val classFqName = aClass?.fqName?.asString() ?: return emptyList()
        return findUrlsForClass(classFqName, aClass.containingKtFile)
    }

    private fun findUrlsForClass(aClass: PsiClass?): List<String> {
        val classFqName = aClass?.qualifiedName ?: return emptyList()
        val pkgFqName = PsiUtil.getPackageName(aClass) ?: return emptyList()
        val virtualFile = aClass.containingFile?.originalFile?.virtualFile ?: return emptyList()
        return findUrlsForClass(pkgFqName, classFqName, aClass.project, virtualFile)
    }

    private fun findUrlsForEnumEntry(enumEntry: KtEnumEntry?): List<String> {
        val classFqName = enumEntry
            ?.parent
            ?.toUElement()
            ?.getParentOfType(UClass::class.java)
            ?.javaPsi
            ?.qualifiedName
            ?: return emptyList()
        return findUrlsForClass(classFqName, enumEntry.containingKtFile)
            .mapNotNull { classUrl -> enumEntry.name?.let { "$classUrl#$it" } }
    }

    private fun findUrlsForContainingClassOf(ktElement: KtDeclaration): List<String> {
        val containingClass = getContainingLightClassForKtDeclaration(ktElement) ?: return emptyList()
        val classFqName = containingClass.qualifiedName ?: return emptyList()
        return findUrlsForClass(classFqName, ktElement.containingKtFile)
    }

    private fun findUrlsForClass(classFqName: String, containingKtFile: KtFile): List<String> {
        val virtualFile = containingKtFile.originalFile.virtualFile ?: return emptyList()
        val pkgName = containingKtFile.packageFqName.asString()
        return findUrlsForClass(pkgName, classFqName, containingKtFile.project, virtualFile)
    }

    private fun findUrlsForClass(
        pkgFqName: String,
        classFqName: String,
        project: Project,
        virtualFile: VirtualFile
    ): List<String> {
        val relPath = when {
            pkgFqName.isEmpty() -> "[root]/$classFqName"
            else -> pkgFqName.replace('.', '/') + '/' + classFqName.substring(pkgFqName.length + 1)
        }
        val urls = JavaDocumentationProvider.findUrlForVirtualFile(
            project, virtualFile, relPath + JavaDocumentationProvider.HTML_EXTENSION)
        return urls ?: emptyList()
    }

    private fun findUrlsForLightProperty(
        ktElement: KtDeclaration,
        lightProperty: LightClassUtil.PropertyAccessorsPsiMethods
    ): List<String> {
        val classUrls = findUrlsForContainingClassOf(ktElement)
        return listOf(
            findUrlsForLightElement(classUrls, lightProperty.backingField),
            findUrlsForLightElement(classUrls, lightProperty.setter),
            findUrlsForLightElement(classUrls, lightProperty.getter)
        )
            .flatten()
    }

    private fun findUrlsForLightElement(ktElement: KtDeclaration, lightElement: PsiElement?): List<String> {
        return findUrlsForLightElement(findUrlsForContainingClassOf(ktElement), lightElement)
    }

    private fun findUrlsForLightElement(classUrls: List<String>, lightElement: PsiElement?): List<String> {
        if (lightElement == null) {
            return emptyList()
        }
        val elementSignatures = getLightElementSignatures(lightElement)
        return classUrls.flatMap { classUrl -> elementSignatures.map { signature -> "$classUrl#$signature" } }
    }

    private fun getLightElementSignatures(element: PsiElement): List<String> {
        return when (element) {
            is PsiField -> listOf(element.name)
            is PsiMethod -> {
                listOf(element.name + "(", element.name + "-")
                // JavaDocumentationProvider.getHtmlMethodSignatures(element, LanguageLevel.HIGHEST).toList()

                // Unfortunately, currently Dokka generates anchors for function in a different format than Java's `javadoc`,
                // e.g. functions params have simple non-qualified names.
                // We haven't yet decided whether we want to change Dokka's result format to coincide or
                // to implement anchors generation for Dokka's variant here, so by now we are just looking for all overloads
                // and show them all.
            }
            else -> emptyList()
        }
    }

    private fun getContainingLightClassForKtDeclaration(declaration: KtDeclaration): PsiClass? {
        return when {
            declaration is KtFunction && declaration.isLocal -> null
            else -> declaration.toUElementOfType<UMethod>()?.uastParent?.javaPsi?.castSafelyTo<PsiClass>()
        }
    }
}


/**
 * Fetches and extracts docs for the given element from urls to the JavaDoc.html
 * which were generated by the [KotlinExternalDocUrlsProvider].
 */
class KotlinDocExtractorFromJavaDoc(private val project: Project) : AbstractExternalFilter() {

    private var myCurrentProcessingElement: PsiElement? = null

    /**
     * Converts a relative link into
     * `com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL PSI_ELEMENT_PROTOCOL`
     * -type link if possible, so that by pressing on links in quick doc we could open docs right inside the same quick doc hint.
     */
    private val myReferenceConverters = arrayOf<RefConvertor>(
        object : RefConvertor(HREF_REGEXP) {
            override fun convertReference(root: String, href: String): String {
                if (BrowserUtil.isAbsoluteURL(href)) {
                    return href
                }
                val reference = myCurrentProcessingElement?.let { JavaDocInfoGenerator.createReferenceForRelativeLink(href, it) }
                return when {
                    reference != null -> reference
                    href.startsWith("#") -> root + href
                    else -> {
                        val nakedRoot = ourHtmlFileSuffix.matcher(root).replaceAll("/")
                        doAnnihilate(nakedRoot + href)
                    }
                }
            }
        }
    )

    override fun getRefConverters(): Array<RefConvertor> {
        return myReferenceConverters
    }

    @Throws(Exception::class)
    @Nls
    override fun getExternalDocInfoForElement(elementDocUrl: String, element: PsiElement): String? {
        myCurrentProcessingElement = element

        val externalDoc = tryGetDocFromBuiltInServer(elementDocUrl)
            ?: super.getExternalDocInfoForElement(elementDocUrl, element)

        myCurrentProcessingElement = null

        return externalDoc?.let { buildResultDocPage(elementDocUrl, generateSignature(element), it) }
    }

    @VisibleForTesting
    fun getExternalDocInfoForElement(elementDocUrl: String, javaDocPageHtml: String): String {
        val content = javaDocPageHtml.toByteArray()
        val contentStream = ByteArrayInputStream(content)
        val contentReader = MyReader(contentStream, StandardCharsets.UTF_8.name())

        val parsedDocs = buildString { contentReader.use { reader -> doBuildFromStream(elementDocUrl, reader, this) } }
        return correctDocText(elementDocUrl, parsedDocs)
    }

    @Nls
    private fun buildResultDocPage(elementDocUrl: String, elementSignature: String?, elementDoc: String): String {
        @NlsSafe
        @Language("HTML")
        val result = """
                <html>
                <head>
                  ${VfsUtilCore.convertToURL(elementDocUrl)?.let { "<base href=\"$it\">" } ?: ""}
                  <style type="text/css">
                    ul.inheritance {
                      margin: 0;
                      padding: 0;
                    }
                    ul.inheritance li {
                      display: inline;
                      list-style-type: none;
                    }
                    ul.inheritance li ul.inheritance {
                      margin-left: 15px;
                      padding-left: 15px;
                      padding-top: 1px;
                    }
                    .definition { 
                      padding: 0 0 5px 0;
                      margin: 0 0 5px 0;
                      border-bottom: thin solid #c8cac0; 
                    }
                  </style>
                </head>
    
                <body>
                  ${elementSignature ?: ""}
                  ${elementDoc}
                </body>
                </html>
            """.trimIndent()

        return result
    }

    private fun generateSignature(element: PsiElement): String? {
        return runReadAction { KotlinDocumentationProvider().generateDoc(element, null) }
            ?.let { Jsoup.parse(it) }
            ?.getElementsByClass("definition")
            ?.first()
            ?.outerHtml()
    }

    @NlsSafe
    private fun tryGetDocFromBuiltInServer(elementDocUrl: String): String? {
        val projectPath = "/" + project.name + "/"
        val urlToBuiltInServer = "http://localhost:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}$projectPath"

        if (!elementDocUrl.startsWith(urlToBuiltInServer)) return null
        val url = Urls.parseFromIdea(elementDocUrl) ?: return null
        val file = WebServerPathToFileManager.getInstance(project).findVirtualFile(url.path.substring(projectPath.length)) ?: return null

        val content = file.inputStream.use { it.readAllBytes() }
        val contentStream = ByteArrayInputStream(content)
        val contentReader = MyReader(contentStream, StandardCharsets.UTF_8.name())

        val parsedDocs = buildString { contentReader.use { reader -> doBuildFromStream(elementDocUrl, reader, this) } }
        return correctDocText(elementDocUrl, parsedDocs)
    }

    override fun doBuildFromStream(url: String, input: Reader, data: StringBuilder) {
        if (input !is MyReader) return
        val root = Jsoup.parse(input.inputStream, input.encoding, url)

        val anchor = url.substringAfterLast("#", "")

        if (anchor.isBlank()) {
            data.append(root.findClassDescriptionFromRoot() ?: "")
        } else {
            val anchors = root.findAnchors(anchor)
            val elementDescription = when {
                anchors.size == 1 -> {
                    anchors.single().let {
                        it.findClassMemberElementDescriptionFromAnchor()
                            ?: it.findEnumEntryDescriptionFromAnchor()
                    }
                }
                else -> {
                    val overloads = anchors.mapNotNull { it.findClassMemberElementDescriptionFromAnchor(doIncludeSignature = true) }

                    @Language("HTML")
                    val result = """
                        <h3 style="margin-bottom: 0; font-weight: bold;">Found ${overloads.size} overloads:</h3>
                        <ol>
                           ${overloads.joinToString(separator = "\n\n") { "<li style='margin: 0 0 8px 0;'>$it</li>" }}
                        </ol>
                    """.trimIndent()
                    result
                }
            }
            data.append(elementDescription ?: "")
        }
    }

    companion object {
        private val HREF_REGEXP = Pattern.compile("<A.*?HREF=\"([^>\"]*)\"", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    }
}


/* ------------------------------------------------------------------------------------------- */
//region Utilities for extracting docs from the javadoc.html by element anchors

private fun Element.findAnchors(anchorName: String): List<Element> {
    return select("a[name^=$anchorName], a[id^=$anchorName], a[href\$=$anchorName]")
}

private fun Element.findClassDescriptionFromRoot(): String? {
    return getElementsByClass("description").first()?.findElementDescriptionFromUlBlockList()
}

private fun Element.findClassMemberElementDescriptionFromAnchor(doIncludeSignature: Boolean = false): String? {
    return nextElementSibling()?.findElementDescriptionFromUlBlockList(doIncludeSignature)
}

private fun Element.findElementDescriptionFromUlBlockList(doIncludeSignature: Boolean = false): String? {
    val infoHolder = selectFirst("li.blockList") ?: return null
    val description = infoHolder.getElementsByClass("block").first() ?: return null
    val signature = description.previousElementSibling()?.takeIf { it.tag().normalName() == "pre" }
    val tagsDictionary = description.nextElementSibling()?.takeIf { it.tag().normalName() == "dl" }

    return buildString {
        if (doIncludeSignature && signature != null) {
            append(signature.outerHtml())
        }
        append(description.outerHtml())
        if (tagsDictionary != null) {
            append(tagsDictionary.outerHtml())
        }
    }
}

private fun Element.findEnumEntryDescriptionFromAnchor(): String? {
    val description = parents().firstOrNull { it.tag().normalName() == "th" }?.nextElementSibling()?.children() ?: return null
    return """
        <div>
          ${description.outerHtml()}
        </div>
        """.trimIndent()
}

//endregion
/* ------------------------------------------------------------------------------------------- */