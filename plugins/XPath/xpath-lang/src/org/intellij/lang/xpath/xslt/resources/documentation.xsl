<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns="http://www.w3.org/1999/xhtml"
                xmlns:h="http://www.w3.org/1999/xhtml"
                xmlns:x="urn:xslt-documentation"
                exclude-result-prefixes="x h">

  <xsl:param name="element" />
  <xsl:param name="type" select="'element'" />

  <xsl:output method="html" />

  <xsl:template match="x:ref">
    <xsl:param name="base" />
    <xsl:variable name="name" select="@name" />
    <xsl:variable name="category" select="local-name(..)" />

    <xsl:apply-templates select="//x:*[local-name() = $category and @name = $name]/*">
      <xsl:with-param name="base" select="$base" />
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="h:a[@href]">
    <xsl:param name="base" />

    <xsl:variable name="href" select="@href" />
    <xsl:choose>
      <xsl:when test="starts-with($href, concat($base, '#'))">
        <xsl:call-template name="make-link">
          <xsl:with-param name="ref" select="substring(substring-after($href, $base), 2)" />
          <xsl:with-param name="base" select="$base" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="starts-with($href, '#')">
        <xsl:call-template name="make-link">
          <xsl:with-param name="ref" select="substring($href, 2)" />
          <xsl:with-param name="base" select="$base" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="contains($href, '#') and //x:*[@base = substring-before($href, '#')]">
        <xsl:variable name="otherbase" select="//x:*[@base = substring-before($href, '#')]" />
        <xsl:variable name="ref" select="substring-after($href, '#')" />
        <xsl:call-template name="make-link">
          <xsl:with-param name="ref" select="$ref" />
          <xsl:with-param name="base" select="$otherbase/@base" />
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <a class="external">
          <xsl:apply-templates select="@* | node()" />
        </a>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="make-link">
    <xsl:param name="ref" />
    <xsl:param name="base" />

    <!--<xsl:message>ref:<xsl:value-of select="$ref" /></xsl:message>-->
    <xsl:variable name="self" select="ancestor::x:*[1]" />
    <!--<xsl:message>self:<xsl:value-of select="concat(name($self), ' ', $self/@name)" /></xsl:message>-->
    <xsl:variable name="target" select="//h:a[@name = $ref]" />

    <xsl:choose>
      <xsl:when test="$target">
        <xsl:variable name="other" select="$target/ancestor::x:*[1]" />
        <!--<xsl:message>other:<xsl:value-of select="concat(name($other), ' ', $other/@name)" /></xsl:message>-->
        <xsl:choose>
          <xsl:when test="count($other | $self) > 1">
            <a href="psi_element://{local-name($other)}${$other/@name}">
              <xsl:copy-of select="node()" />
            </a>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="." />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <a class="external" href="{$base}#{$ref}"><xsl:copy-of select="./node()"/></a>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="text() | @* | h:*">
    <xsl:param name="base" />
    <xsl:copy>
      <xsl:apply-templates select="@*|node()">
        <xsl:with-param name="base" select="$base" />
      </xsl:apply-templates>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="/">
    <xsl:variable name="node" select="//x:*[local-name() = $type and @name = $element]" />
    <xsl:variable name="found" select="boolean($node)" />

    <html x:found="{$found}" x:href="{concat($node/../@base, $node/@href)}">
      <xsl:if test="$found">
        <xsl:variable name="style-doc" select="document('doc-style.xhtml')" />
        <head>
          <xsl:copy-of select="$style-doc//h:style[@id = $node/ancestor::x:*[@style]/@style]" />
        </head>
        <body>
          <xsl:apply-templates select="$node/* | $node/text()">
            <xsl:with-param name="base" select="$node/../@base" />
          </xsl:apply-templates>
          <xsl:copy-of select="$style-doc//*[@id = concat($node/../@style, '-footer')]" />
        </body>
      </xsl:if>
    </html>
  </xsl:template>

</xsl:stylesheet>