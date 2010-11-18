<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="html" indent="yes" encoding="UTF-8"
              doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN"
              doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"/>
  <xsl:decimal-format decimal-separator="." grouping-separator=","/>
  <xsl:template match="testrun">
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>
          <xsl:text disable-output-escaping="yes">Test Results &amp;mdash; </xsl:text>
          <xsl:value-of select="@name"/>
        </title>

        <style type="text/css">
          html {
            height: 100%
          }

          body {
            margin: 0 auto;
            padding: 0;
            text-align: left;
            height: 100%;
            font-family: myriad, arial, tahoma, verdana, sans-serif;
            color: #151515;
            font-size: 90%;
            line-height: 1.3em;
            background-color: #fff;
          }

          * {
            margin: 0;
            padding: 0
          }

          .clr {
            clear: both;
            overflow: hidden;
          }

          img {
            border: none
          }

          a {
            color: #0046b0;
            text-decoration: none;
          }

          a:hover {
            text-decoration: none;
          }

          a:focus, a:active {
            outline: none
          }

          .noborder {
            border: none
          }

          h1 {
            color: #151515;
            font-size: 180%;
            line-height: 1.1em;
            font-weight: bold;
          }

          h2 {
            color: #393D42;
            font-size: 160%;
            font-weight: normal
          }

          h3 {
            font-size: 120%;
            font-weight: bold;
            margin-bottom: .5em
          }

          h4 {
            font-size: 110%;
          }

          h5 {
            font-size: 110%;
          }

          span.failed {
            color: #ff0000
          }

          span.error {
            color: #ff0000
          }

          span.passed {
            color: #1d9d01
          }

          span.ignored {
            color: #fff600
          }

          span.skipped {
            color: #fff600
          }

          hr {
            background-color: blue
          }

          #container {
            min-width: 30em;
          }

          #header {
            padding: 0;
            position: fixed;
            width: 100%;
            z-index: 10;
            background-color: #c7ceda;
          }

          #header h1 {
            margin: 1em 3em 1em 1.7em;
          }

          #header h1 strong {
            white-space: nowrap;
          }

          #header .time {
            margin-top: 2.2em;
            margin-right: 3.4em;
            float: right;
          }

          #treecontrol {
            margin: 0;
            padding: .5em 3em .5em 0;
            text-align: right;
            background-color: #fff;
          }

          #treecontrol ul li {
            display: inline;
            list-style: none;
            color: #666;
          }

          #content {
            padding: 0 2.5em 2em 1.7em;
          }

          #content ul {
            margin: .4em 0 .1em 2em;
            list-style: none;
          }

          #content ul li.level {
            cursor: pointer;
          }

          #content ul li.level span {
            display: block;
            font-weight: bold;
          }

          #content ul li.level.top {
            margin-bottom: .3em;
          }

          #content ul li.level.top > span {
            padding: .5em 0 .5em 1em;
            font-size: 120%;
            color: #151515;
            background-color: #f2f2f2;
            border-left: solid 10px #93e078;
          }

          #content ul li.level.top.failed > span {
            border-left: solid 10px #f02525;
          }

          #content ul li.level.top.ignored > span {
            border-left: solid 10px #f8d216;
          }

          #content ul li.level.suite > span {
            margin-bottom: .8em;
            padding: 0 0 0 .8em;
            display: block;
            font-size: 110%;
            line-height: 1em;
            color: #151515;
            border-left: solid 15px #93e078;
          }

          #content ul li.level.suite.failed > span {
            border-left: solid 15px #f02525;
          }

          #content ul li.level.suite.ignored > span {
            border-left: solid 15px #f8d216;
          }

          #content ul li.level.suite > ul {
            margin-bottom: 1.5em;
          }

          #content ul li.level.test > span {
            padding: .3em 0 .3em 1em;
            color: #0046b0;
            font-size: 100%;
            border-left: solid 6px #93e078;
            border-bottom: solid 1px #dbdbdb;
          }

          #content ul li.level.test.failed > span {
            border-left: solid 6px #f02525;
          }

          #content ul li.level.test.ignored > span {
            border-left: solid 6px #f8d216;
          }

          #content ul li.text p, #content ul li.text span {
            margin-bottom: 1.5em;
            color: #151515 !important;
            font-size: 90% !important;
            font-weight: normal !important;
            overflow-x: auto;
            cursor: auto !important;
            background: none !important;
            border: none !important;
          }

          #content ul li.text span {
            margin-bottom: 0;
            display: block;
          }

          #content ul li.text span.stderr {
            color: #8b0000 !important;
          }

          #content ul li .time {
            margin-right: .5em;
            width: 5em;
            text-align: right;
            font-size: 13px;
            color: #151515;
            font-style: normal;
            font-weight: normal;
            float: right;
          }

          #content ul li span .status {
            width: 6em;
            font-size: 90%;
            color: #1d9d01;
            font-style: normal;
            font-weight: normal;
            float: right;
            text-align: right;
          }

          #content ul li.failed > span .status {
            color: #ff0000;
          }

          #content ul li.ignored > span .status {
            color: #d6b000;
          }

          #footer {
              height: 2em;
              background-color: #c7ceda;
          }
          #footer p {
              padding: .4em 0 0 3.6em;
              font-size: 80%;
          }
        </style>
        <xsl:text disable-output-escaping="yes"><![CDATA[

<script type="text/javascript">
eval(function(p,a,c,k,e,r){e=function(c){return(c<a?'':e(parseInt(c/a)))+((c=c%a)>35?String.fromCharCode(c+29):c.toString(36))};if(!''.replace(/^/,String)){while(c--)r[e(c)]=k[c]||e(c);k=[function(e){return r[e]}];e=function(){return'\\w+'};c=1};while(c--)if(k[c])p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c]);return p}('(G(){9(1n E!="11")H w=E;H E=17.16=G(a,c){9(17==6||!6.4K)I 1q E(a,c);I 6.4K(a,c)};9(1n $!="11")H D=$;17.$=E;H u=/^[^<]*(<(.|\\s)+>)[^>]*$|^#(\\w+)$/;E.1b=E.3x={4K:G(a,c){a=a||U;9(1n a=="1M"){H m=u.2L(a);9(m&&(m[1]||!c)){9(m[1])a=E.4I([m[1]],c);J{H b=U.42(m[3]);9(b)9(b.1T!=m[3])I E().1X(a);J{6[0]=b;6.L=1;I 6}J a=[]}}J I 1q E(c).1X(a)}J 9(E.1p(a))I 1q E(U)[E.1b.2d?"2d":"37"](a);I 6.6u(a.1d==1C&&a||(a.4d||a.L&&a!=17&&!a.1z&&a[0]!=11&&a[0].1z)&&E.2h(a)||[a])},4d:"1.2",82:G(){I 6.L},L:0,28:G(a){I a==11?E.2h(6):6[a]},2v:G(a){H b=E(a);b.4Y=6;I b},6u:G(a){6.L=0;1C.3x.1a.15(6,a);I 6},O:G(a,b){I E.O(6,a,b)},4J:G(a){H b=-1;6.O(G(i){9(6==a)b=i});I b},1y:G(f,d,e){H c=f;9(f.1d==3T)9(d==11)I 6.L&&E[e||"1y"](6[0],f)||11;J{c={};c[f]=d}I 6.O(G(a){M(H b 1j c)E.1y(e?6.R:6,b,E.1c(6,c[b],e,a,b))})},18:G(b,a){I 6.1y(b,a,"3O")},2t:G(e){9(1n e!="5w"&&e!=S)I 6.4o().3e(U.6F(e));H t="";E.O(e||6,G(){E.O(6.2X,G(){9(6.1z!=8)t+=6.1z!=1?6.6x:E.1b.2t([6])})});I t},5l:G(b){9(6[0])E(b,6[0].3N).6t().38(6[0]).1W(G(){H a=6;1Y(a.1t)a=a.1t;I a}).3e(6);I 6},8p:G(a){I 6.O(G(){E(6).6p().5l(a)})},8h:G(a){I 6.O(G(){E(6).5l(a)})},3e:G(){I 6.3s(1k,Q,1,G(a){6.57(a)})},6k:G(){I 6.3s(1k,Q,-1,G(a){6.38(a,6.1t)})},6g:G(){I 6.3s(1k,P,1,G(a){6.14.38(a,6)})},53:G(){I 6.3s(1k,P,-1,G(a){6.14.38(a,6.2i)})},1B:G(){I 6.4Y||E([])},1X:G(t){H b=E.1W(6,G(a){I E.1X(t,a)});I 6.2v(/[^+>] [^+>]/.12(t)||t.1e("..")>-1?E.4V(b):b)},6t:G(e){H f=6.1W(G(){I 6.66?E(6.66)[0]:6.4S(Q)});9(e===Q){H d=f.1X("*").4Q();6.1X("*").4Q().O(G(i){H c=E.K(6,"2A");M(H a 1j c)M(H b 1j c[a])E.1h.1f(d[i],a,c[a][b],c[a][b].K)})}I f},1A:G(t){I 6.2v(E.1p(t)&&E.2T(6,G(b,a){I t.15(b,[a])})||E.3G(t,6))},5T:G(t){I 6.2v(t.1d==3T&&E.3G(t,6,Q)||E.2T(6,G(a){I(t.1d==1C||t.4d)?E.2S(a,t)<0:a!=t}))},1f:G(t){I 6.2v(E.1S(6.28(),t.1d==3T?E(t).28():t.L!=11&&(!t.Y||t.Y=="7t")?t:[t]))},3j:G(a){I a?E.3G(a,6).L>0:P},7g:G(a){I 6.3j("."+a)},2V:G(b){9(b==11){9(6.L){H c=6[0];9(E.Y(c,"24")){H e=c.4z,a=[],W=c.W,2P=c.N=="24-2P";9(e<0)I S;M(H i=2P?e:0,2Y=2P?e+1:W.L;i<2Y;i++){H d=W[i];9(d.29){H b=E.V.1g&&!d.70["1N"].9U?d.2t:d.1N;9(2P)I b;a.1a(b)}}I a}J I 6[0].1N.1o(/\\r/g,"")}}J I 6.O(G(){9(b.1d==1C&&/4s|5u/.12(6.N))6.2K=(E.2S(6.1N,b)>=0||E.2S(6.2J,b)>=0);J 9(E.Y(6,"24")){H a=b.1d==1C?b:[b];E("9m",6).O(G(){6.29=(E.2S(6.1N,a)>=0||E.2S(6.2t,a)>=0)});9(!a.L)6.4z=-1}J 6.1N=b})},4n:G(a){I a==11?(6.L?6[0].3D:S):6.4o().3e(a)},6H:G(a){I 6.53(a).2e()},2s:G(){I 6.2v(1C.3x.2s.15(6,1k))},1W:G(b){I 6.2v(E.1W(6,G(a,i){I b.3c(a,i,a)}))},4Q:G(){I 6.1f(6.4Y)},3s:G(f,d,g,e){H c=6.L>1,a;I 6.O(G(){9(!a){a=E.4I(f,6.3N);9(g<0)a.91()}H b=6;9(d&&E.Y(6,"1F")&&E.Y(a[0],"4k"))b=6.4q("1J")[0]||6.57(U.5r("1J"));E.O(a,G(){9(E.Y(6,"1P")){9(6.3g)E.3w({1u:6.3g,3h:P,1Z:"1P"});J E.5h(6.2t||6.6s||6.3D||"")}J e.15(b,[c?6.4S(Q):6])})})}};E.1i=E.1b.1i=G(){H c=1k[0]||{},a=1,2g=1k.L,5e=P;9(c.1d==8v){5e=c;c=1k[1]||{}}9(2g==1){c=6;a=0}H b;M(;a<2g;a++)9((b=1k[a])!=S)M(H i 1j b){9(c==b[i])6r;9(5e&&1n b[i]==\'5w\'&&c[i])E.1i(c[i],b[i]);J 9(b[i]!=11)c[i]=b[i]}I c};H F="16"+(1q 3v()).3u(),6q=0,5d={};E.1i({8k:G(a){17.$=D;9(a)17.16=w;I E},1p:G(a){I!!a&&1n a!="1M"&&!a.Y&&a.1d!=1C&&/G/i.12(a+"")},4a:G(a){I a.35&&!a.1K||a.34&&a.3N&&!a.3N.1K},5h:G(a){a=E.33(a);9(a){9(17.6o)17.6o(a);J 9(E.V.1H)17.58(a,0);J 3p.3c(17,a)}},Y:G(b,a){I b.Y&&b.Y.26()==a.26()},1I:{},K:G(c,d,b){c=c==17?5d:c;H a=c[F];9(!a)a=c[F]=++6q;9(d&&!E.1I[a])E.1I[a]={};9(b!=11)E.1I[a][d]=b;I d?E.1I[a][d]:a},30:G(c,b){c=c==17?5d:c;H a=c[F];9(b){9(E.1I[a]){2G E.1I[a][b];b="";M(b 1j E.1I[a])22;9(!b)E.30(c)}}J{2c{2G c[F]}27(e){9(c.54)c.54(F)}2G E.1I[a]}},O:G(a,b,c){9(c){9(a.L==11)M(H i 1j a)b.15(a[i],c);J M(H i=0,45=a.L;i<45;i++)9(b.15(a[i],c)===P)22}J{9(a.L==11)M(H i 1j a)b.3c(a[i],i,a[i]);J M(H i=0,45=a.L,2V=a[0];i<45&&b.3c(2V,i,2V)!==P;2V=a[++i]){}}I a},1c:G(c,b,d,e,a){9(E.1p(b))b=b.3c(c,[e]);H f=/z-?4J|7T-?7S|1v|69|7Q-?1G/i;I b&&b.1d==4X&&d=="3O"&&!f.12(a)?b+"2I":b},1m:{1f:G(b,c){E.O((c||"").2p(/\\s+/),G(i,a){9(!E.1m.3t(b.1m,a))b.1m+=(b.1m?" ":"")+a})},2e:G(b,c){b.1m=c!=11?E.2T(b.1m.2p(/\\s+/),G(a){I!E.1m.3t(c,a)}).65(" "):""},3t:G(t,c){I E.2S(c,(t.1m||t).3z().2p(/\\s+/))>-1}},2q:G(e,o,f){M(H i 1j o){e.R["3C"+i]=e.R[i];e.R[i]=o[i]}f.15(e,[]);M(H i 1j o)e.R[i]=e.R["3C"+i]},18:G(e,p){9(p=="1G"||p=="2E"){H b={},3Z,3Y,d=["7L","7K","7J","7G"];E.O(d,G(){b["7F"+6]=0;b["7D"+6+"61"]=0});E.2q(e,b,G(){9(E(e).3j(\':3X\')){3Z=e.7A;3Y=e.7z}J{e=E(e.4S(Q)).1X(":4s").5X("2K").1B().18({4v:"1O",2W:"4D",19:"2U",7w:"0",1R:"0"}).5P(e.14)[0];H a=E.18(e.14,"2W")||"3V";9(a=="3V")e.14.R.2W="7k";3Z=e.7h;3Y=e.7f;9(a=="3V")e.14.R.2W="3V";e.14.3k(e)}});I p=="1G"?3Z:3Y}I E.3O(e,p)},3O:G(h,j,i){H g,2u=[],2q=[];G 3l(a){9(!E.V.1H)I P;H b=U.3M.3P(a,S);I!b||b.4y("3l")==""}9(j=="1v"&&E.V.1g){g=E.1y(h.R,"1v");I g==""?"1":g}9(j.1U(/4r/i))j=y;9(!i&&h.R[j])g=h.R[j];J 9(U.3M&&U.3M.3P){9(j.1U(/4r/i))j="4r";j=j.1o(/([A-Z])/g,"-$1").2F();H d=U.3M.3P(h,S);9(d&&!3l(h))g=d.4y(j);J{M(H a=h;a&&3l(a);a=a.14)2u.4Z(a);M(a=0;a<2u.L;a++)9(3l(2u[a])){2q[a]=2u[a].R.19;2u[a].R.19="2U"}g=j=="19"&&2q[2u.L-1]!=S?"2j":U.3M.3P(h,S).4y(j)||"";M(a=0;a<2q.L;a++)9(2q[a]!=S)2u[a].R.19=2q[a]}9(j=="1v"&&g=="")g="1"}J 9(h.43){H f=j.1o(/\\-(\\w)/g,G(m,c){I c.26()});g=h.43[j]||h.43[f];9(!/^\\d+(2I)?$/i.12(g)&&/^\\d/.12(g)){H k=h.R.1R;H e=h.4t.1R;h.4t.1R=h.43.1R;h.R.1R=g||0;g=h.R.74+"2I";h.R.1R=k;h.4t.1R=e}}I g},4I:G(a,e){H r=[];e=e||U;E.O(a,G(i,d){9(!d)I;9(d.1d==4X)d=d.3z();9(1n d=="1M"){d=d.1o(/(<(\\w+)[^>]*?)\\/>/g,G(m,a,b){I b.1U(/^(71|6Z|5D|6Y|49|9S|9P|3f|9K|9I)$/i)?m:a+"></"+b+">"});H s=E.33(d).2F(),1r=e.5r("1r"),2x=[];H c=!s.1e("<9D")&&[1,"<24>","</24>"]||!s.1e("<9A")&&[1,"<6S>","</6S>"]||s.1U(/^<(9x|1J|9u|9t|9s)/)&&[1,"<1F>","</1F>"]||!s.1e("<4k")&&[2,"<1F><1J>","</1J></1F>"]||(!s.1e("<9r")||!s.1e("<9q"))&&[3,"<1F><1J><4k>","</4k></1J></1F>"]||!s.1e("<5D")&&[2,"<1F><1J></1J><6L>","</6L></1F>"]||E.V.1g&&[1,"1r<1r>","</1r>"]||[0,"",""];1r.3D=c[1]+d+c[2];1Y(c[0]--)1r=1r.5k;9(E.V.1g){9(!s.1e("<1F")&&s.1e("<1J")<0)2x=1r.1t&&1r.1t.2X;J 9(c[1]=="<1F>"&&s.1e("<1J")<0)2x=1r.2X;M(H n=2x.L-1;n>=0;--n)9(E.Y(2x[n],"1J")&&!2x[n].2X.L)2x[n].14.3k(2x[n]);9(/^\\s/.12(d))1r.38(e.6F(d.1U(/^\\s*/)[0]),1r.1t)}d=E.2h(1r.2X)}9(0===d.L&&(!E.Y(d,"3B")&&!E.Y(d,"24")))I;9(d[0]==11||E.Y(d,"3B")||d.W)r.1a(d);J r=E.1S(r,d)});I r},1y:G(c,d,a){H e=E.4a(c)?{}:E.5o;9(d=="29"&&E.V.1H)c.14.4z;9(e[d]){9(a!=11)c[e[d]]=a;I c[e[d]]}J 9(E.V.1g&&d=="R")I E.1y(c.R,"9g",a);J 9(a==11&&E.V.1g&&E.Y(c,"3B")&&(d=="9e"||d=="9d"))I c.9b(d).6x;J 9(c.34){9(a!=11){9(d=="N"&&E.Y(c,"49")&&c.14)6E"N 96 94\'t 93 92";c.90(d,a)}9(E.V.1g&&/6B|3g/.12(d)&&!E.4a(c))I c.4l(d,2);I c.4l(d)}J{9(d=="1v"&&E.V.1g){9(a!=11){c.69=1;c.1A=(c.1A||"").1o(/6A\\([^)]*\\)/,"")+(3K(a).3z()=="8V"?"":"6A(1v="+a*6z+")")}I c.1A?(3K(c.1A.1U(/1v=([^)]*)/)[1])/6z).3z():""}d=d.1o(/-([a-z])/8T,G(z,b){I b.26()});9(a!=11)c[d]=a;I c[d]}},33:G(t){I(t||"").1o(/^\\s+|\\s+$/g,"")},2h:G(a){H r=[];9(1n a!="8Q")M(H i=0,2g=a.L;i<2g;i++)r.1a(a[i]);J r=a.2s(0);I r},2S:G(b,a){M(H i=0,2g=a.L;i<2g;i++)9(a[i]==b)I i;I-1},1S:G(a,b){9(E.V.1g){M(H i=0;b[i];i++)9(b[i].1z!=8)a.1a(b[i])}J M(H i=0;b[i];i++)a.1a(b[i]);I a},4V:G(b){H r=[],2f={};2c{M(H i=0,6P=b.L;i<6P;i++){H a=E.K(b[i]);9(!2f[a]){2f[a]=Q;r.1a(b[i])}}}27(e){r=b}I r},2T:G(b,a,c){9(1n a=="1M")a=3p("P||G(a,i){I "+a+"}");H d=[];M(H i=0,4m=b.L;i<4m;i++)9(!c&&a(b[i],i)||c&&!a(b[i],i))d.1a(b[i]);I d},1W:G(c,b){9(1n b=="1M")b=3p("P||G(a){I "+b+"}");H d=[];M(H i=0,4m=c.L;i<4m;i++){H a=b(c[i],i);9(a!==S&&a!=11){9(a.1d!=1C)a=[a];d=d.8O(a)}}I d}});H v=8M.8K.2F();E.V={4f:(v.1U(/.+(?:8I|8H|8F|8E)[\\/: ]([\\d.]+)/)||[])[1],1H:/6T/.12(v),3a:/3a/.12(v),1g:/1g/.12(v)&&!/3a/.12(v),39:/39/.12(v)&&!/(8B|6T)/.12(v)};H y=E.V.1g?"4h":"5g";E.1i({5f:!E.V.1g||U.8A=="8z",4h:E.V.1g?"4h":"5g",5o:{"M":"8y","8x":"1m","4r":y,5g:y,4h:y,3D:"3D",1m:"1m",1N:"1N",36:"36",2K:"2K",8w:"8u",29:"29",8t:"8s"}});E.O({1D:"a.14",8r:"16.4e(a,\'14\')",8q:"16.2R(a,2,\'2i\')",8o:"16.2R(a,2,\'4c\')",8n:"16.4e(a,\'2i\')",8m:"16.4e(a,\'4c\')",8l:"16.5c(a.14.1t,a)",8j:"16.5c(a.1t)",6p:"16.Y(a,\'8i\')?a.8f||a.8e.U:16.2h(a.2X)"},G(i,n){E.1b[i]=G(a){H b=E.1W(6,n);9(a&&1n a=="1M")b=E.3G(a,b);I 6.2v(E.4V(b))}});E.O({5P:"3e",8d:"6k",38:"6g",8c:"53",8b:"6H"},G(i,n){E.1b[i]=G(){H a=1k;I 6.O(G(){M(H j=0,2g=a.L;j<2g;j++)E(a[j])[n](6)})}});E.O({5X:G(a){E.1y(6,a,"");6.54(a)},8a:G(c){E.1m.1f(6,c)},89:G(c){E.1m.2e(6,c)},88:G(c){E.1m[E.1m.3t(6,c)?"2e":"1f"](6,c)},2e:G(a){9(!a||E.1A(a,[6]).r.L){E.30(6);6.14.3k(6)}},4o:G(){E("*",6).O(G(){E.30(6)});1Y(6.1t)6.3k(6.1t)}},G(i,n){E.1b[i]=G(){I 6.O(n,1k)}});E.O(["87","61"],G(i,a){H n=a.2F();E.1b[n]=G(h){I 6[0]==17?E.V.1H&&3r["86"+a]||E.5f&&32.2Y(U.35["59"+a],U.1K["59"+a])||U.1K["59"+a]:6[0]==U?32.2Y(U.1K["6n"+a],U.1K["6m"+a]):h==11?(6.L?E.18(6[0],n):S):6.18(n,h.1d==3T?h:h+"2I")}});H C=E.V.1H&&3q(E.V.4f)<85?"(?:[\\\\w*56-]|\\\\\\\\.)":"(?:[\\\\w\\84-\\83*56-]|\\\\\\\\.)",6j=1q 47("^>\\\\s*("+C+"+)"),6i=1q 47("^("+C+"+)(#)("+C+"+)"),6h=1q 47("^([#.]?)("+C+"*)");E.1i({55:{"":"m[2]==\'*\'||16.Y(a,m[2])","#":"a.4l(\'1T\')==m[2]",":":{81:"i<m[3]-0",7Z:"i>m[3]-0",2R:"m[3]-0==i",7Y:"m[3]-0==i",3o:"i==0",3n:"i==r.L-1",6f:"i%2==0",6d:"i%2","3o-46":"a.14.4q(\'*\')[0]==a","3n-46":"16.2R(a.14.5k,1,\'4c\')==a","7X-46":"!16.2R(a.14.5k,2,\'4c\')",1D:"a.1t",4o:"!a.1t",7W:"(a.6s||a.7V||\'\').1e(m[3])>=0",3X:\'"1O"!=a.N&&16.18(a,"19")!="2j"&&16.18(a,"4v")!="1O"\',1O:\'"1O"==a.N||16.18(a,"19")=="2j"||16.18(a,"4v")=="1O"\',7U:"!a.36",36:"a.36",2K:"a.2K",29:"a.29||16.1y(a,\'29\')",2t:"\'2t\'==a.N",4s:"\'4s\'==a.N",5u:"\'5u\'==a.N",52:"\'52\'==a.N",51:"\'51\'==a.N",50:"\'50\'==a.N",6c:"\'6c\'==a.N",6b:"\'6b\'==a.N",2y:\'"2y"==a.N||16.Y(a,"2y")\',49:"/49|24|6a|2y/i.12(a.Y)",3t:"16.1X(m[3],a).L",7R:"/h\\\\d/i.12(a.Y)",7P:"16.2T(16.2Z,G(1b){I a==1b.T;}).L"}},68:[/^(\\[) *@?([\\w-]+) *([!*$^~=]*) *(\'?"?)(.*?)\\4 *\\]/,/^(:)([\\w-]+)\\("?\'?(.*?(\\(.*?\\))?[^(]*?)"?\'?\\)/,1q 47("^([:.#]*)("+C+"+)")],3G:G(a,c,b){H d,2b=[];1Y(a&&a!=d){d=a;H f=E.1A(a,c,b);a=f.t.1o(/^\\s*,\\s*/,"");2b=b?c=f.r:E.1S(2b,f.r)}I 2b},1X:G(t,o){9(1n t!="1M")I[t];9(o&&!o.1z)o=S;o=o||U;H d=[o],2f=[],3n;1Y(t&&3n!=t){H r=[];3n=t;t=E.33(t);H l=P;H g=6j;H m=g.2L(t);9(m){H p=m[1].26();M(H i=0;d[i];i++)M(H c=d[i].1t;c;c=c.2i)9(c.1z==1&&(p=="*"||c.Y.26()==p.26()))r.1a(c);d=r;t=t.1o(g,"");9(t.1e(" ")==0)6r;l=Q}J{g=/^([>+~])\\s*(\\w*)/i;9((m=g.2L(t))!=S){r=[];H p=m[2],1S={};m=m[1];M(H j=0,31=d.L;j<31;j++){H n=m=="~"||m=="+"?d[j].2i:d[j].1t;M(;n;n=n.2i)9(n.1z==1){H h=E.K(n);9(m=="~"&&1S[h])22;9(!p||n.Y.26()==p.26()){9(m=="~")1S[h]=Q;r.1a(n)}9(m=="+")22}}d=r;t=E.33(t.1o(g,""));l=Q}}9(t&&!l){9(!t.1e(",")){9(o==d[0])d.44();2f=E.1S(2f,d);r=d=[o];t=" "+t.67(1,t.L)}J{H k=6i;H m=k.2L(t);9(m){m=[0,m[2],m[3],m[1]]}J{k=6h;m=k.2L(t)}m[2]=m[2].1o(/\\\\/g,"");H f=d[d.L-1];9(m[1]=="#"&&f&&f.42&&!E.4a(f)){H q=f.42(m[2]);9((E.V.1g||E.V.3a)&&q&&1n q.1T=="1M"&&q.1T!=m[2])q=E(\'[@1T="\'+m[2]+\'"]\',f)[0];d=r=q&&(!m[3]||E.Y(q,m[3]))?[q]:[]}J{M(H i=0;d[i];i++){H a=m[1]=="#"&&m[3]?m[3]:m[1]!=""||m[0]==""?"*":m[2];9(a=="*"&&d[i].Y.2F()=="5w")a="3f";r=E.1S(r,d[i].4q(a))}9(m[1]==".")r=E.4W(r,m[2]);9(m[1]=="#"){H e=[];M(H i=0;r[i];i++)9(r[i].4l("1T")==m[2]){e=[r[i]];22}r=e}d=r}t=t.1o(k,"")}}9(t){H b=E.1A(t,r);d=r=b.r;t=E.33(b.t)}}9(t)d=[];9(d&&o==d[0])d.44();2f=E.1S(2f,d);I 2f},4W:G(r,m,a){m=" "+m+" ";H c=[];M(H i=0;r[i];i++){H b=(" "+r[i].1m+" ").1e(m)>=0;9(!a&&b||a&&!b)c.1a(r[i])}I c},1A:G(t,r,h){H d;1Y(t&&t!=d){d=t;H p=E.68,m;M(H i=0;p[i];i++){m=p[i].2L(t);9(m){t=t.7O(m[0].L);m[2]=m[2].1o(/\\\\/g,"");22}}9(!m)22;9(m[1]==":"&&m[2]=="5T")r=E.1A(m[3],r,Q).r;J 9(m[1]==".")r=E.4W(r,m[2],h);J 9(m[1]=="["){H g=[],N=m[3];M(H i=0,31=r.L;i<31;i++){H a=r[i],z=a[E.5o[m[2]]||m[2]];9(z==S||/6B|3g|29/.12(m[2]))z=E.1y(a,m[2])||\'\';9((N==""&&!!z||N=="="&&z==m[5]||N=="!="&&z!=m[5]||N=="^="&&z&&!z.1e(m[5])||N=="$="&&z.67(z.L-m[5].L)==m[5]||(N=="*="||N=="~=")&&z.1e(m[5])>=0)^h)g.1a(a)}r=g}J 9(m[1]==":"&&m[2]=="2R-46"){H e={},g=[],12=/(\\d*)n\\+?(\\d*)/.2L(m[3]=="6f"&&"2n"||m[3]=="6d"&&"2n+1"||!/\\D/.12(m[3])&&"n+"+m[3]||m[3]),3o=(12[1]||1)-0,d=12[2]-0;M(H i=0,31=r.L;i<31;i++){H j=r[i],14=j.14,1T=E.K(14);9(!e[1T]){H c=1;M(H n=14.1t;n;n=n.2i)9(n.1z==1)n.4U=c++;e[1T]=Q}H b=P;9(3o==1){9(d==0||j.4U==d)b=Q}J 9((j.4U+d)%3o==0)b=Q;9(b^h)g.1a(j)}r=g}J{H f=E.55[m[1]];9(1n f!="1M")f=E.55[m[1]][m[2]];f=3p("P||G(a,i){I "+f+"}");r=E.2T(r,f,h)}}I{r:r,t:t}},4e:G(b,c){H d=[];H a=b[c];1Y(a&&a!=U){9(a.1z==1)d.1a(a);a=a[c]}I d},2R:G(a,e,c,b){e=e||1;H d=0;M(;a;a=a[c])9(a.1z==1&&++d==e)22;I a},5c:G(n,a){H r=[];M(;n;n=n.2i){9(n.1z==1&&(!a||n!=a))r.1a(n)}I r}});E.1h={1f:G(g,e,c,h){9(E.V.1g&&g.41!=11)g=17;9(!c.2r)c.2r=6.2r++;9(h!=11){H d=c;c=G(){I d.15(6,1k)};c.K=h;c.2r=d.2r}H i=e.2p(".");e=i[0];c.N=i[1];H b=E.K(g,"2A")||E.K(g,"2A",{});H f=E.K(g,"2m",G(){H a;9(1n E=="11"||E.1h.4T)I a;a=E.1h.2m.15(g,1k);I a});H j=b[e];9(!j){j=b[e]={};9(g.4R)g.4R(e,f,P);J g.7N("40"+e,f)}j[c.2r]=c;6.23[e]=Q},2r:1,23:{},2e:G(d,c,b){H e=E.K(d,"2A"),2O,4J;9(1n c=="1M"){H a=c.2p(".");c=a[0]}9(e){9(c&&c.N){b=c.4P;c=c.N}9(!c){M(c 1j e)6.2e(d,c)}J 9(e[c]){9(b)2G e[c][b.2r];J M(b 1j e[c])9(!a[1]||e[c][b].N==a[1])2G e[c][b];M(2O 1j e[c])22;9(!2O){9(d.4O)d.4O(c,E.K(d,"2m"),P);J d.7M("40"+c,E.K(d,"2m"));2O=S;2G e[c]}}M(2O 1j e)22;9(!2O){E.30(d,"2A");E.30(d,"2m")}}},1L:G(d,b,e,c,f){b=E.2h(b||[]);9(!e){9(6.23[d])E("*").1f([17,U]).1L(d,b)}J{H a,2O,1b=E.1p(e[d]||S),4N=!b[0]||!b[0].2B;9(4N)b.4Z(6.4M({N:d,2o:e}));9(E.1p(E.K(e,"2m")))a=E.K(e,"2m").15(e,b);9(!1b&&e["40"+d]&&e["40"+d].15(e,b)===P)a=P;9(4N)b.44();9(f&&f.15(e,b)===P)a=P;9(1b&&c!==P&&a!==P&&!(E.Y(e,\'a\')&&d=="4L")){6.4T=Q;e[d]()}6.4T=P}I a},2m:G(d){H a;d=E.1h.4M(d||17.1h||{});H b=d.N.2p(".");d.N=b[0];H c=E.K(6,"2A")&&E.K(6,"2A")[d.N],3m=1C.3x.2s.3c(1k,1);3m.4Z(d);M(H j 1j c){3m[0].4P=c[j];3m[0].K=c[j].K;9(!b[1]||c[j].N==b[1]){H e=c[j].15(6,3m);9(a!==P)a=e;9(e===P){d.2B();d.3L()}}}9(E.V.1g)d.2o=d.2B=d.3L=d.4P=d.K=S;I a},4M:G(c){H a=c;c=E.1i({},a);c.2B=G(){9(a.2B)a.2B();a.7I=P};c.3L=G(){9(a.3L)a.3L();a.7H=Q};9(!c.2o&&c.64)c.2o=c.64;9(E.V.1H&&c.2o.1z==3)c.2o=a.2o.14;9(!c.4H&&c.4G)c.4H=c.4G==c.2o?c.7E:c.4G;9(c.63==S&&c.62!=S){H e=U.35,b=U.1K;c.63=c.62+(e&&e.2D||b.2D||0);c.7C=c.7B+(e&&e.2z||b.2z||0)}9(!c.3R&&(c.60||c.5Z))c.3R=c.60||c.5Z;9(!c.5Y&&c.5W)c.5Y=c.5W;9(!c.3R&&c.2y)c.3R=(c.2y&1?1:(c.2y&2?3:(c.2y&4?2:0)));I c}};E.1b.1i({3Q:G(c,a,b){I c=="5V"?6.2P(c,a,b):6.O(G(){E.1h.1f(6,c,b||a,b&&a)})},2P:G(d,b,c){I 6.O(G(){E.1h.1f(6,d,G(a){E(6).5U(a);I(c||b).15(6,1k)},c&&b)})},5U:G(a,b){I 6.O(G(){E.1h.2e(6,a,b)})},1L:G(c,a,b){I 6.O(G(){E.1h.1L(c,a,6,Q,b)})},7y:G(c,a,b){9(6[0])I E.1h.1L(c,a,6[0],P,b)},25:G(){H a=1k;I 6.4L(G(e){6.4E=0==6.4E?1:0;e.2B();I a[6.4E].15(6,[e])||P})},7x:G(f,g){G 4x(e){H p=e.4H;1Y(p&&p!=6)2c{p=p.14}27(e){p=6};9(p==6)I P;I(e.N=="4w"?f:g).15(6,[e])}I 6.4w(4x).5S(4x)},2d:G(f){5R();9(E.3W)f.15(U,[E]);J E.3i.1a(G(){I f.15(6,[E])});I 6}});E.1i({3W:P,3i:[],2d:G(){9(!E.3W){E.3W=Q;9(E.3i){E.O(E.3i,G(){6.15(U)});E.3i=S}9(E.V.39||E.V.3a)U.4O("5Q",E.2d,P);9(!17.7v.L)E(17).37(G(){E("#4C").2e()})}}});E.O(("7u,7o,37,7n,6n,5V,4L,7m,"+"7l,7j,7i,4w,5S,7p,24,"+"50,7q,7r,7s,3U").2p(","),G(i,o){E.1b[o]=G(f){I f?6.3Q(o,f):6.1L(o)}});H x=P;G 5R(){9(x)I;x=Q;9(E.V.39||E.V.3a)U.4R("5Q",E.2d,P);J 9(E.V.1g){U.7e("<7d"+"7c 1T=4C 7b=Q "+"3g=//:><\\/1P>");H a=U.42("4C");9(a)a.5O=G(){9(6.2C!="1l")I;E.2d()};a=S}J 9(E.V.1H)E.4B=41(G(){9(U.2C=="5N"||U.2C=="1l"){4A(E.4B);E.4B=S;E.2d()}},10);E.1h.1f(17,"37",E.2d)}E.1b.1i({37:G(g,d,c){9(E.1p(g))I 6.3Q("37",g);H e=g.1e(" ");9(e>=0){H i=g.2s(e,g.L);g=g.2s(0,e)}c=c||G(){};H f="4F";9(d)9(E.1p(d)){c=d;d=S}J{d=E.3f(d);f="5M"}H h=6;E.3w({1u:g,N:f,K:d,1l:G(a,b){9(b=="1E"||b=="5L")h.4n(i?E("<1r/>").3e(a.4p.1o(/<1P(.|\\s)*?\\/1P>/g,"")).1X(i):a.4p);58(G(){h.O(c,[a.4p,b,a])},13)}});I 6},7a:G(){I E.3f(6.5K())},5K:G(){I 6.1W(G(){I E.Y(6,"3B")?E.2h(6.79):6}).1A(G(){I 6.2J&&!6.36&&(6.2K||/24|6a/i.12(6.Y)||/2t|1O|51/i.12(6.N))}).1W(G(i,c){H b=E(6).2V();I b==S?S:b.1d==1C?E.1W(b,G(i,a){I{2J:c.2J,1N:a}}):{2J:c.2J,1N:b}}).28()}});E.O("5J,5I,5H,6e,5G,5F".2p(","),G(i,o){E.1b[o]=G(f){I 6.3Q(o,f)}});H B=(1q 3v).3u();E.1i({28:G(d,b,a,c){9(E.1p(b)){a=b;b=S}I E.3w({N:"4F",1u:d,K:b,1E:a,1Z:c})},78:G(b,a){I E.28(b,S,a,"1P")},77:G(c,b,a){I E.28(c,b,a,"3S")},76:G(d,b,a,c){9(E.1p(b)){a=b;b={}}I E.3w({N:"5M",1u:d,K:b,1E:a,1Z:c})},80:G(a){E.1i(E.4u,a)},4u:{23:Q,N:"4F",2H:0,5E:"75/x-73-3B-72",6l:Q,3h:Q,K:S},4b:{},3w:G(s){H f,48=/=(\\?|%3F)/g,1s,K;s=E.1i(Q,s,E.1i(Q,{},E.4u,s));9(s.K&&s.6l&&1n s.K!="1M")s.K=E.3f(s.K);H q=s.1u.1e("?");9(q>-1){s.K=(s.K?s.K+"&":"")+s.1u.2s(q+1);s.1u=s.1u.2s(0,q)}9(s.1Z=="5b"){9(!s.K||!s.K.1U(48))s.K=(s.K?s.K+"&":"")+(s.5b||"6X")+"=?";s.1Z="3S"}9(s.1Z=="3S"&&s.K&&s.K.1U(48)){f="5b"+B++;s.K=s.K.1o(48,"="+f);s.1Z="1P";17[f]=G(a){K=a;1E();17[f]=11;2c{2G 17[f]}27(e){}}}9(s.1Z=="1P"&&s.1I==S)s.1I=P;9(s.1I===P&&s.N.2F()=="28")s.K=(s.K?s.K+"&":"")+"56="+(1q 3v()).3u();9(s.K&&s.N.2F()=="28"){s.1u+="?"+s.K;s.K=S}9(s.23&&!E.5a++)E.1h.1L("5J");9(!s.1u.1e("6W")&&s.1Z=="1P"){H h=U.4q("8g")[0];H g=U.5r("1P");g.3g=s.1u;9(!f&&(s.1E||s.1l)){H j=P;g.9Q=g.5O=G(){9(!j&&(!6.2C||6.2C=="5N"||6.2C=="1l")){j=Q;1E();1l();h.3k(g)}}}h.57(g);I}H k=P;H i=17.6V?1q 6V("9O.9N"):1q 6U();i.9M(s.N,s.1u,s.3h);9(s.K)i.5B("9J-9H",s.5E);9(s.5A)i.5B("9G-5z-9F",E.4b[s.1u]||"9E, 9C 9B 9z 5v:5v:5v 9y");i.5B("X-9w-9v","6U");9(s.6R)s.6R(i);9(s.23)E.1h.1L("5F",[i,s]);H c=G(a){9(!k&&i&&(i.2C==4||a=="2H")){k=Q;9(d){4A(d);d=S}1s=a=="2H"&&"2H"||!E.6Q(i)&&"3U"||s.5A&&E.6O(i,s.1u)&&"5L"||"1E";9(1s=="1E"){2c{K=E.6N(i,s.1Z)}27(e){1s="5t"}}9(1s=="1E"){H b;2c{b=i.5i("6M-5z")}27(e){}9(s.5A&&b)E.4b[s.1u]=b;9(!f)1E()}J E.5s(s,i,1s);1l();9(s.3h)i=S}};9(s.3h){H d=41(c,13);9(s.2H>0)58(G(){9(i){i.9p();9(!k)c("2H")}},s.2H)}2c{i.9n(s.K)}27(e){E.5s(s,i,S,e)}9(!s.3h)c();I i;G 1E(){9(s.1E)s.1E(K,1s);9(s.23)E.1h.1L("5G",[i,s])}G 1l(){9(s.1l)s.1l(i,1s);9(s.23)E.1h.1L("5H",[i,s]);9(s.23&&!--E.5a)E.1h.1L("5I")}},5s:G(s,a,b,e){9(s.3U)s.3U(a,b,e);9(s.23)E.1h.1L("6e",[a,s,e])},5a:0,6Q:G(r){2c{I!r.1s&&9l.9k=="52:"||(r.1s>=6K&&r.1s<9j)||r.1s==6J||E.V.1H&&r.1s==11}27(e){}I P},6O:G(a,c){2c{H b=a.5i("6M-5z");I a.1s==6J||b==E.4b[c]||E.V.1H&&a.1s==11}27(e){}I P},6N:G(r,b){H c=r.5i("9i-N");H d=b=="6y"||!b&&c&&c.1e("6y")>=0;H a=d?r.9h:r.4p;9(d&&a.35.34=="5t")6E"5t";9(b=="1P")E.5h(a);9(b=="3S")a=3p("("+a+")");I a},3f:G(a){H s=[];9(a.1d==1C||a.4d)E.O(a,G(){s.1a(3b(6.2J)+"="+3b(6.1N))});J M(H j 1j a)9(a[j]&&a[j].1d==1C)E.O(a[j],G(){s.1a(3b(j)+"="+3b(6))});J s.1a(3b(j)+"="+3b(a[j]));I s.65("&").1o(/%20/g,"+")}});E.1b.1i({1x:G(b,a){I b?6.1V({1G:"1x",2E:"1x",1v:"1x"},b,a):6.1A(":1O").O(G(){6.R.19=6.3d?6.3d:"";9(E.18(6,"19")=="2j")6.R.19="2U"}).1B()},1w:G(b,a){I b?6.1V({1G:"1w",2E:"1w",1v:"1w"},b,a):6.1A(":3X").O(G(){6.3d=6.3d||E.18(6,"19");9(6.3d=="2j")6.3d="2U";6.R.19="2j"}).1B()},6G:E.1b.25,25:G(a,b){I E.1p(a)&&E.1p(b)?6.6G(a,b):a?6.1V({1G:"25",2E:"25",1v:"25"},a,b):6.O(G(){E(6)[E(6).3j(":1O")?"1x":"1w"]()})},9c:G(b,a){I 6.1V({1G:"1x"},b,a)},9a:G(b,a){I 6.1V({1G:"1w"},b,a)},99:G(b,a){I 6.1V({1G:"25"},b,a)},98:G(b,a){I 6.1V({1v:"1x"},b,a)},97:G(b,a){I 6.1V({1v:"1w"},b,a)},95:G(c,a,b){I 6.1V({1v:a},c,b)},1V:G(j,h,g,f){H i=E.6C(h,g,f);I 6[i.3I===P?"O":"3I"](G(){i=E.1i({},i);H d=E(6).3j(":1O"),3r=6;M(H p 1j j){9(j[p]=="1w"&&d||j[p]=="1x"&&!d)I E.1p(i.1l)&&i.1l.15(6);9(p=="1G"||p=="2E"){i.19=E.18(6,"19");i.2N=6.R.2N}}9(i.2N!=S)6.R.2N="1O";i.3H=E.1i({},j);E.O(j,G(c,a){H e=1q E.2w(3r,i,c);9(/25|1x|1w/.12(a))e[a=="25"?d?"1x":"1w":a](j);J{H b=a.3z().1U(/^([+-]?)([\\d.]+)(.*)$/),1Q=e.2b(Q)||0;9(b){1B=3K(b[2]),2k=b[3]||"2I";9(2k!="2I"){3r.R[c]=1B+2k;1Q=(1B/e.2b(Q))*1Q;3r.R[c]=1Q+2k}9(b[1])1B=((b[1]=="-"?-1:1)*1B)+1Q;e.3J(1Q,1B,2k)}J e.3J(1Q,a,"")}});I Q})},3I:G(a,b){9(!b){b=a;a="2w"}9(!1k.L)I A(6[0],a);I 6.O(G(){9(b.1d==1C)A(6,a,b);J{A(6,a).1a(b);9(A(6,a).L==1)b.15(6)}})},9f:G(){H a=E.2Z;I 6.O(G(){M(H i=0;i<a.L;i++)9(a[i].T==6)a.6D(i--,1)}).5p()}});H A=G(b,c,a){9(!b)I;H q=E.K(b,c+"3I");9(!q||a)q=E.K(b,c+"3I",a?E.2h(a):[]);I q};E.1b.5p=G(a){a=a||"2w";I 6.O(G(){H q=A(6,a);q.44();9(q.L)q[0].15(6)})};E.1i({6C:G(b,a,c){H d=b&&b.1d==8Z?b:{1l:c||!c&&a||E.1p(b)&&b,2a:b,3E:c&&a||a&&a.1d!=8Y&&a};d.2a=(d.2a&&d.2a.1d==4X?d.2a:{8X:8W,8U:6K}[d.2a])||9o;d.3C=d.1l;d.1l=G(){E(6).5p();9(E.1p(d.3C))d.3C.15(6)};I d},3E:{6I:G(p,n,b,a){I b+a*p},5q:G(p,n,b,a){I((-32.8S(p*32.8R)/2)+0.5)*a+b}},2Z:[],2w:G(b,c,a){6.W=c;6.T=b;6.1c=a;9(!c.3A)c.3A={}}});E.2w.3x={4j:G(){9(6.W.2M)6.W.2M.15(6.T,[6.2l,6]);(E.2w.2M[6.1c]||E.2w.2M.6w)(6);9(6.1c=="1G"||6.1c=="2E")6.T.R.19="2U"},2b:G(a){9(6.T[6.1c]!=S&&6.T.R[6.1c]==S)I 6.T[6.1c];H r=3K(E.3O(6.T,6.1c,a));I r&&r>-8P?r:3K(E.18(6.T,6.1c))||0},3J:G(c,b,e){6.5n=(1q 3v()).3u();6.1Q=c;6.1B=b;6.2k=e||6.2k||"2I";6.2l=6.1Q;6.4g=6.4i=0;6.4j();H f=6;G t(){I f.2M()}t.T=6.T;E.2Z.1a(t);9(E.2Z.L==1){H d=41(G(){H a=E.2Z;M(H i=0;i<a.L;i++)9(!a[i]())a.6D(i--,1);9(!a.L)4A(d)},13)}},1x:G(){6.W.3A[6.1c]=E.1y(6.T.R,6.1c);6.W.1x=Q;6.3J(0,6.2b());9(6.1c=="2E"||6.1c=="1G")6.T.R[6.1c]="8N";E(6.T).1x()},1w:G(){6.W.3A[6.1c]=E.1y(6.T.R,6.1c);6.W.1w=Q;6.3J(6.2b(),0)},2M:G(){H t=(1q 3v()).3u();9(t>6.W.2a+6.5n){6.2l=6.1B;6.4g=6.4i=1;6.4j();6.W.3H[6.1c]=Q;H a=Q;M(H i 1j 6.W.3H)9(6.W.3H[i]!==Q)a=P;9(a){9(6.W.19!=S){6.T.R.2N=6.W.2N;6.T.R.19=6.W.19;9(E.18(6.T,"19")=="2j")6.T.R.19="2U"}9(6.W.1w)6.T.R.19="2j";9(6.W.1w||6.W.1x)M(H p 1j 6.W.3H)E.1y(6.T.R,p,6.W.3A[p])}9(a&&E.1p(6.W.1l))6.W.1l.15(6.T);I P}J{H n=t-6.5n;6.4i=n/6.W.2a;6.4g=E.3E[6.W.3E||(E.3E.5q?"5q":"6I")](6.4i,n,0,1,6.W.2a);6.2l=6.1Q+((6.1B-6.1Q)*6.4g);6.4j()}I Q}};E.2w.2M={2D:G(a){a.T.2D=a.2l},2z:G(a){a.T.2z=a.2l},1v:G(a){E.1y(a.T.R,"1v",a.2l)},6w:G(a){a.T.R[a.1c]=a.2l+a.2k}};E.1b.6m=G(){H c=0,3y=0,T=6[0],5m;9(T)8L(E.V){H b=E.18(T,"2W")=="4D",1D=T.14,21=T.21,2Q=T.3N,5y=1H&&!b&&3q(4f)<8J;9(T.6v){5x=T.6v();1f(5x.1R+32.2Y(2Q.35.2D,2Q.1K.2D),5x.3y+32.2Y(2Q.35.2z,2Q.1K.2z));9(1g){H d=E("4n").18("9L");d=(d=="8G"||E.5f&&3q(4f)>=7)&&2||d;1f(-d,-d)}}J{1f(T.5j,T.5C);1Y(21){1f(21.5j,21.5C);9(39&&/^t[d|h]$/i.12(1D.34)||!5y)d(21);9(5y&&!b&&E.18(21,"2W")=="4D")b=Q;21=21.21}1Y(1D.34&&/^1K|4n$/i.12(1D.34)){9(/^8D|1F-9R.*$/i.12(E.18(1D,"19")))1f(-1D.2D,-1D.2z);9(39&&E.18(1D,"2N")!="3X")d(1D);1D=1D.14}9(1H&&b)1f(-2Q.1K.5j,-2Q.1K.5C)}5m={3y:3y,1R:c}}I 5m;G d(a){1f(E.18(a,"8C"),E.18(a,"9T"))}G 1f(l,t){c+=3q(l)||0;3y+=3q(t)||0}}})();',62,615,'||||||this|||if|||||||||||||||||||||||||||||||||function|var|return|else|data|length|for|type|each|false|true|style|null|elem|document|browser|options||nodeName|||undefined|test||parentNode|apply|jQuery|window|css|display|push|fn|prop|constructor|indexOf|add|msie|event|extend|in|arguments|complete|className|typeof|replace|isFunction|new|div|status|firstChild|url|opacity|hide|show|attr|nodeType|filter|end|Array|parent|success|table|height|safari|cache|tbody|body|trigger|string|value|hidden|script|start|left|merge|id|match|animate|map|find|while|dataType||offsetParent|break|global|select|toggle|toUpperCase|catch|get|selected|duration|cur|try|ready|remove|done|al|makeArray|nextSibling|none|unit|now|handle||target|split|swap|guid|slice|text|stack|pushStack|fx|tb|button|scrollTop|events|preventDefault|readyState|scrollLeft|width|toLowerCase|delete|timeout|px|name|checked|exec|step|overflow|ret|one|doc|nth|inArray|grep|block|val|position|childNodes|max|timers|removeData|rl|Math|trim|tagName|documentElement|disabled|load|insertBefore|mozilla|opera|encodeURIComponent|call|oldblock|append|param|src|async|readyList|is|removeChild|color|args|last|first|eval|parseInt|self|domManip|has|getTime|Date|ajax|prototype|top|toString|orig|form|old|innerHTML|easing||multiFilter|curAnim|queue|custom|parseFloat|stopPropagation|defaultView|ownerDocument|curCSS|getComputedStyle|bind|which|json|String|error|static|isReady|visible|oWidth|oHeight|on|setInterval|getElementById|currentStyle|shift|ol|child|RegExp|jsre|input|isXMLDoc|lastModified|previousSibling|jquery|dir|version|pos|styleFloat|state|update|tr|getAttribute|el|html|empty|responseText|getElementsByTagName|float|radio|runtimeStyle|ajaxSettings|visibility|mouseover|handleHover|getPropertyValue|selectedIndex|clearInterval|safariTimer|__ie_init|absolute|lastToggle|GET|fromElement|relatedTarget|clean|index|init|click|fix|evt|removeEventListener|handler|andSelf|addEventListener|cloneNode|triggered|nodeIndex|unique|classFilter|Number|prevObject|unshift|submit|password|file|after|removeAttribute|expr|_|appendChild|setTimeout|client|active|jsonp|sibling|win|deep|boxModel|cssFloat|globalEval|getResponseHeader|offsetLeft|lastChild|wrapAll|results|startTime|props|dequeue|swing|createElement|handleError|parsererror|checkbox|00|object|box|safari2|Modified|ifModified|setRequestHeader|offsetTop|col|contentType|ajaxSend|ajaxSuccess|ajaxComplete|ajaxStop|ajaxStart|serializeArray|notmodified|POST|loaded|onreadystatechange|appendTo|DOMContentLoaded|bindReady|mouseout|not|unbind|unload|ctrlKey|removeAttr|metaKey|keyCode|charCode|Width|clientX|pageX|srcElement|join|outerHTML|substr|parse|zoom|textarea|reset|image|odd|ajaxError|even|before|quickClass|quickID|quickChild|prepend|processData|offset|scroll|execScript|contents|uuid|continue|textContent|clone|setArray|getBoundingClientRect|_default|nodeValue|xml|100|alpha|href|speed|splice|throw|createTextNode|_toggle|replaceWith|linear|304|200|colgroup|Last|httpData|httpNotModified|fl|httpSuccess|beforeSend|fieldset|webkit|XMLHttpRequest|ActiveXObject|http|callback|img|br|attributes|abbr|urlencoded|www|pixelLeft|application|post|getJSON|getScript|elements|serialize|defer|ipt|scr|write|clientWidth|hasClass|clientHeight|mousemove|mouseup|relative|mousedown|dblclick|resize|focus|change|keydown|keypress|keyup|FORM|blur|frames|right|hover|triggerHandler|offsetWidth|offsetHeight|clientY|pageY|border|toElement|padding|Left|cancelBubble|returnValue|Right|Bottom|Top|detachEvent|attachEvent|substring|animated|line|header|weight|font|enabled|innerText|contains|only|eq|gt|ajaxSetup|lt|size|uFFFF|u0128|417|inner|Height|toggleClass|removeClass|addClass|replaceAll|insertAfter|prependTo|contentWindow|contentDocument|head|wrap|iframe|children|noConflict|siblings|prevAll|nextAll|prev|wrapInner|next|parents|maxLength|maxlength|readOnly|Boolean|readonly|class|htmlFor|CSS1Compat|compatMode|compatible|borderLeftWidth|inline|ie|ra|medium|it|rv|522|userAgent|with|navigator|1px|concat|10000|array|PI|cos|ig|fast|NaN|600|slow|Function|Object|setAttribute|reverse|changed|be|can|fadeTo|property|fadeOut|fadeIn|slideToggle|slideUp|getAttributeNode|slideDown|method|action|stop|cssText|responseXML|content|300|protocol|location|option|send|400|abort|th|td|cap|colg|tfoot|With|Requested|thead|GMT|1970|leg|Jan|01|opt|Thu|Since|If|Type|area|Content|hr|borderWidth|open|XMLHTTP|Microsoft|meta|onload|row|link|borderTopWidth|specified'.split('|'),0,{}))
</script>
<script type="text/javascript">
jQuery.cookie = function(name, value, options) {
    if (typeof value != 'undefined') { // name and value given, set cookie
        options = options || {};
        if (value === null) {
            value = '';
            options.expires = -1;
        }
        var expires = '';
        if (options.expires && (typeof options.expires == 'number' || options.expires.toUTCString)) {
            var date;
            if (typeof options.expires == 'number') {
                date = new Date();
                date.setTime(date.getTime() + (options.expires * 24 * 60 * 60 * 1000));
            } else {
                date = options.expires;
            }
            expires = '; expires=' + date.toUTCString(); // use expires attribute, max-age is not supported by IE
        }
        var path = options.path ? '; path=' + options.path : '';
        var domain = options.domain ? '; domain=' + options.domain : '';
        var secure = options.secure ? '; secure' : '';
        document.cookie = [name, '=', encodeURIComponent(value), expires, path, domain, secure].join('');
    } else { // only name given, get cookie
        var cookieValue = null;
        if (document.cookie && document.cookie != '') {
            var cookies = document.cookie.split(';');
            for (var i = 0; i < cookies.length; i++) {
                var cookie = jQuery.trim(cookies[i]);
                // Does this cookie string begin with the name we want?
                if (cookie.substring(0, name.length + 1) == (name + '=')) {
                    cookieValue = decodeURIComponent(cookie.substring(name.length + 1));
                    break;
                }
            }
        }
        return cookieValue;
    }
};
</script>
<script type="text/javascript">
(function($) {

	$.extend($.fn, {
		swapClass: function(c1, c2) {
			var c1Elements = this.filter('.' + c1);
			this.filter('.' + c2).removeClass(c2).addClass(c1);
			c1Elements.removeClass(c1).addClass(c2);
			return this;
		},
		replaceClass: function(c1, c2) {
			return this.filter('.' + c1).removeClass(c1).addClass(c2).end();
		},
		hoverClass: function(className) {
			className = className || "hover";
			return this.hover(function() {
				$(this).addClass(className);
			}, function() {
				$(this).removeClass(className);
			});
		},
		heightToggle: function(animated, callback) {
			animated ?
				this.animate({ height: "toggle" }, animated, callback) :
				this.each(function(){
					jQuery(this)[ jQuery(this).is(":hidden") ? "show" : "hide" ]();
					if(callback)
						callback.apply(this, arguments);
				});
		},
		heightHide: function(animated, callback) {
			if (animated) {
				this.animate({ height: "hide" }, animated, callback);
			} else {
				this.hide();
				if (callback)
					this.each(callback);
			}
		},
		prepareBranches: function(settings) {
			if (!settings.prerendered) {
				// mark last tree items
				this.filter(":last-child:not(ul)").addClass(CLASSES.last);
				// collapse whole tree, or only those marked as closed, anyway except those marked as open
				this.filter((settings.collapsed ? "" : "." + CLASSES.closed) + ":not(." + CLASSES.open + ")").find(">ul").hide();
			}
			// return all items with sublists
			return this.filter(":has(>ul)");
		},
		applyClasses: function(settings, toggler) {
			this.filter(":has(>ul):not(:has(>a))").find(">span").click(function(event) {
				toggler.apply($(this).next());
			}).add( $("a", this) ).hoverClass();

			if (!settings.prerendered) {
				// handle closed ones first
				this.filter(":has(>ul:hidden)")
						.addClass(CLASSES.expandable)
						.replaceClass(CLASSES.last, CLASSES.lastExpandable);

				// handle open ones
				this.not(":has(>ul:hidden)")
						.addClass(CLASSES.collapsable)
						.replaceClass(CLASSES.last, CLASSES.lastCollapsable);

	            // create hitarea
				this.prepend("<div class=\"" + CLASSES.hitarea + "\"/>").find("div." + CLASSES.hitarea).each(function() {
					var classes = "";
					$.each($(this).parent().attr("class").split(" "), function() {
						classes += this + "-hitarea ";
					});
					$(this).addClass( classes );
				});
			}

			// apply event to hitarea
			this.find("div." + CLASSES.hitarea).click( toggler );
		},
		treeview: function(settings) {

			settings = $.extend({
				cookieId: "treeview"
			}, settings);

			if (settings.add) {
				return this.trigger("add", [settings.add]);
			}

			if ( settings.toggle ) {
				var callback = settings.toggle;
				settings.toggle = function() {
					return callback.apply($(this).parent()[0], arguments);
				};
			}

			// factory for treecontroller
			function treeController(tree, control) {
				// factory for click handlers
				function handler(filter) {
					return function() {
						// reuse toggle event handler, applying the elements to toggle
						// start searching for all hitareas
						toggler.apply( $("div." + CLASSES.hitarea, tree).filter(function() {
							// for plain toggle, no filter is provided, otherwise we need to check the parent element
							return filter ? $(this).parent("." + filter).length : true;
						}) );
						return false;
					};
				}
				// click on first element to collapse tree
				$("a:eq(0)", control).click( handler(CLASSES.collapsable) );
				// click on second to expand tree
				$("a:eq(1)", control).click( handler(CLASSES.expandable) );
				// click on third to toggle tree
				$("a:eq(2)", control).click( handler() );
			}

			// handle toggle event
			function toggler() {
				$(this)
					.parent()
					// swap classes for hitarea
					.find(">.hitarea")
						.swapClass( CLASSES.collapsableHitarea, CLASSES.expandableHitarea )
						.swapClass( CLASSES.lastCollapsableHitarea, CLASSES.lastExpandableHitarea )
					.end()
					// swap classes for parent li
					.swapClass( CLASSES.collapsable, CLASSES.expandable )
					.swapClass( CLASSES.lastCollapsable, CLASSES.lastExpandable )
					// find child lists
					.find( ">ul" )
					// toggle them
					.heightToggle( settings.animated, settings.toggle );
				if ( settings.unique ) {
					$(this).parent()
						.siblings()
						// swap classes for hitarea
						.find(">.hitarea")
							.replaceClass( CLASSES.collapsableHitarea, CLASSES.expandableHitarea )
							.replaceClass( CLASSES.lastCollapsableHitarea, CLASSES.lastExpandableHitarea )
						.end()
						.replaceClass( CLASSES.collapsable, CLASSES.expandable )
						.replaceClass( CLASSES.lastCollapsable, CLASSES.lastExpandable )
						.find( ">ul" )
						.heightHide( settings.animated, settings.toggle );
				}
			}

			function serialize() {
				function binary(arg) {
					return arg ? 1 : 0;
				}
				var data = [];
				branches.each(function(i, e) {
					data[i] = $(e).is(":has(>ul:visible)") ? 1 : 0;
				});
				$.cookie(settings.cookieId, data.join("") );
			}

			function deserialize() {
				var stored = $.cookie(settings.cookieId);
				if ( stored ) {
					var data = stored.split("");
					branches.each(function(i, e) {
						$(e).find(">ul")[ parseInt(data[i]) ? "show" : "hide" ]();
					});
				}
			}

			// add treeview class to activate styles
			this.addClass("treeview");

			// prepare branches and find all tree items with child lists
			var branches = this.find("li").prepareBranches(settings);

			switch(settings.persist) {
			case "cookie":
				var toggleCallback = settings.toggle;
				settings.toggle = function() {
					serialize();
					if (toggleCallback) {
						toggleCallback.apply(this, arguments);
					}
				};
				deserialize();
				break;
			case "location":
				var current = this.find("a").filter(function() { return this.href.toLowerCase() == location.href.toLowerCase(); });
				if ( current.length ) {
					current.addClass("selected").parents("ul, li").add( current.next() ).show();
				}
				break;
			}

			branches.applyClasses(settings, toggler);

			// if control option is set, create the treecontroller and show it
			if ( settings.control ) {
				treeController(this, settings.control);
				$(settings.control).show();
			}

			return this.bind("add", function(event, branches) {
				$(branches).prev()
					.removeClass(CLASSES.last)
					.removeClass(CLASSES.lastCollapsable)
					.removeClass(CLASSES.lastExpandable)
				.find(">.hitarea")
					.removeClass(CLASSES.lastCollapsableHitarea)
					.removeClass(CLASSES.lastExpandableHitarea);
				$(branches).find("li").andSelf().prepareBranches(settings).applyClasses(settings, toggler);
			});
		}
	});

	var CLASSES = $.fn.treeview.classes = {
		open: "open",
		closed: "closed",
		expandable: "expandable",
		expandableHitarea: "expandable-hitarea",
		lastExpandableHitarea: "lastExpandable-hitarea",
		collapsable: "collapsable",
		collapsableHitarea: "collapsable-hitarea",
		lastCollapsableHitarea: "lastCollapsable-hitarea",
		lastCollapsable: "lastCollapsable",
		lastExpandable: "lastExpandable",
		last: "last",
		hitarea: "hitarea"
	};

	$.fn.Treeview = $.fn.treeview;

})(jQuery);
</script>
<script type="text/javascript">
		$(document).ready(function(){
            $("#tree").treeview({
                control: "#treecontrol",
                animated: "fast",
                collapsed: true,
                toggle: function() {
                    window.console && console.log("%o was toggled", this);
                }
            });

            $("#content").css("padding-top", $("#header").height());
        });
	</script>

            ]]></xsl:text>
      </head>
      <body>
        <div id="container">
          <div id="header">
            <xsl:call-template name="duration"/>
            <h1>
              <xsl:value-of select="@name"/>
              <xsl:text>: </xsl:text>
              <strong>
                <xsl:for-each select="count">
                  <xsl:variable name="status" select="@name"/>
                  <span class="{$status}">
                    <xsl:value-of select="@value"/>
                    <xsl:text> </xsl:text>
                    <xsl:value-of select="$status"/>
                    <xsl:if test="count(../count) > position()">
                      <xsl:text>, </xsl:text>
                    </xsl:if>
                  </span>
                </xsl:for-each>
              </strong>
            </h1>
            <div id="treecontrol">
              <ul>
                <li>
                  <a title="Collapse the entire tree below" href="#">Collapse</a>
                  |
                </li>
                <li>
                  <a title="Expand the entire tree below" href="#">Expand</a>
                </li>
              </ul>
            </div>
          </div>
          <div id="content">
            <ul id="tree">
              <xsl:apply-templates/>
            </ul>
          </div>
        </div>
        <div id="footer">
          <p>
            <xsl:value-of select="@footerText"/>
          </p>
        </div>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="suite">
    <xsl:variable name="class">
      <xsl:text>level </xsl:text>

      <xsl:choose>
        <xsl:when test="parent::suite">
          <xsl:text>suite</xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>top</xsl:text>
        </xsl:otherwise>
      </xsl:choose>

      <xsl:call-template name="get-node-class"/>
    </xsl:variable>
    <xsl:element name="li">
      <xsl:attribute name="class">
        <xsl:value-of select="$class"/>
      </xsl:attribute>
      <span>
        <em class="time">
          <xsl:call-template name="duration"/>
        </em>
        <xsl:value-of select="@name"/>
      </span>
      <ul>
        <xsl:apply-templates/>
      </ul>
    </xsl:element>
  </xsl:template>

  <xsl:template match="test">
    <xsl:variable name="class">
      <xsl:text>level test</xsl:text>
      <xsl:call-template name="get-node-class"/>
    </xsl:variable>
    <li class="{$class}">
      <span>
        <em class="time">
          <xsl:call-template name="duration"/>
        </em>
        <em class="status">
          <xsl:value-of select="@status"/>
        </em>
        <xsl:value-of select="@name"/>
      </span>
      <xsl:if test="count(./output) > 0">
        <ul>
          <xsl:for-each select="output">
            <xsl:variable name="displayText">
              <xsl:call-template name="string-replace-all">
                <xsl:with-param name="text" select="text()"/>
                <xsl:with-param name="replace" select="'&#10;'"/>
                <xsl:with-param name="by" select="'&lt;br/>'"/>
              </xsl:call-template>
            </xsl:variable>
            <li class="text">
              <xsl:variable name="output-type" select="@type"/>
              <span class="{$output-type}">
                <xsl:value-of disable-output-escaping="yes" select="$displayText"/>
              </span>
            </li>
          </xsl:for-each>
        </ul>
      </xsl:if>
    </li>
  </xsl:template>

  <xsl:template name="get-node-class">
    <xsl:choose>
      <xsl:when test="@status='failed' or @status='error'">
        <xsl:text> failed open</xsl:text>
      </xsl:when>
      <xsl:when test="@status='ignored' or @status='skipped'">
        <xsl:text> ignored open</xsl:text>
      </xsl:when>
      <xsl:when test="@status='passed'">
        <xsl:if test="name(.) != 'test' and count(parent::*) = 1">
          <xsl:text> open</xsl:text>
        </xsl:if>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="string-replace-all">
    <xsl:param name="text"/>
    <xsl:param name="replace"/>
    <xsl:param name="by"/>
    <xsl:choose>
      <xsl:when test="contains($text, $replace)">
        <xsl:value-of select="substring-before($text,$replace)"/>
        <xsl:value-of select="$by"/>
        <xsl:call-template name="string-replace-all">
          <xsl:with-param name="text" select="substring-after($text,$replace)"/>
          <xsl:with-param name="replace" select="$replace"/>
          <xsl:with-param name="by" select="$by"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$text"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="duration">
    <xsl:if test="@duration">
      <xsl:variable name="msec" select="number(@duration)"/>

      <xsl:variable name="minutes">
        <xsl:choose>
          <xsl:when test="$msec > 60000">
            <xsl:value-of select="floor($msec div 60000)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="0"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:variable>

      <xsl:variable name="seconds" select="($msec - $minutes * 60000) div 1000"/>

      <div class="time">
        <xsl:choose>
          <xsl:when test="$minutes > 0">
            <xsl:value-of select="format-number($minutes, '0')"/>
            <xsl:text> m </xsl:text>
            <xsl:value-of select="format-number($seconds, '0')"/>
            <xsl:text> s</xsl:text>
          </xsl:when>
          <xsl:otherwise>
            <xsl:choose>
              <xsl:when test="$seconds >= 1">
                <xsl:value-of select="format-number($seconds, '0.00')"/>
                <xsl:text> s</xsl:text>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="$msec"/>
                <xsl:text> ms</xsl:text>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:otherwise>
        </xsl:choose>
      </div>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>
