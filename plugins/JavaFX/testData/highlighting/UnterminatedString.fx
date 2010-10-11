<error descr="Missing closing quote">"unterminated {"{2+3} text"} string</error>
;
##<error descr="Missing closing quote">'unterminated {'{2+3} text'} string</error>
;
##[some text]<error descr="Missing closing quote">"unterminated string</error>
;
<error descr="Missing closing quote">"unterminated string\"</error>
;
<error descr="Missing closing quote">'unterminated string\'</error>
;
##[text]<error descr="Missing closing quote">"</error>
;
##<error descr="Missing closing quote">'</error>
;
"Text "  <error descr="Missing closing quote">"unterminated string</error>

"normal string\\";
'another normal string\\';
'';
"";