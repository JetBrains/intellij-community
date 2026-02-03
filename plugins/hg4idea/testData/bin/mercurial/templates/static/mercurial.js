// mercurial.js - JavaScript utility functions
//
// Rendering of branch DAGs on the client side
// Display of elapsed time
// Show or hide diffstat
//
// Copyright 2008 Dirkjan Ochtman <dirkjan AT ochtman DOT nl>
// Copyright 2006 Alexander Schremmer <alex AT alexanderweb DOT de>
//
// derived from code written by Scott James Remnant <scott@ubuntu.com>
// Copyright 2005 Canonical Ltd.
//
// This software may be used and distributed according to the terms
// of the GNU General Public License, incorporated herein by reference.

var colors = [
	[ 1.0, 0.0, 0.0 ],
	[ 1.0, 1.0, 0.0 ],
	[ 0.0, 1.0, 0.0 ],
	[ 0.0, 1.0, 1.0 ],
	[ 0.0, 0.0, 1.0 ],
	[ 1.0, 0.0, 1.0 ]
];

function Graph() {

	this.canvas = document.getElementById('graph');
	this.ctx = this.canvas.getContext('2d');
	this.ctx.strokeStyle = 'rgb(0, 0, 0)';
	this.ctx.fillStyle = 'rgb(0, 0, 0)';
	this.bg = [0, 4];
	this.cell = [2, 0];
	this.columns = 0;

}

Graph.prototype = {
	reset: function() {
		this.bg = [0, 4];
		this.cell = [2, 0];
		this.columns = 0;
	},

	scale: function(height) {
		this.bg_height = height;
		this.box_size = Math.floor(this.bg_height / 1.2);
		this.cell_height = this.box_size;
	},

	setColor: function(color, bg, fg) {

		// Set the colour.
		//
		// If color is a string, expect an hexadecimal RGB
		// value and apply it unchanged. If color is a number,
		// pick a distinct colour based on an internal wheel;
		// the bg parameter provides the value that should be
		// assigned to the 'zero' colours and the fg parameter
		// provides the multiplier that should be applied to
		// the foreground colours.
		var s;
		if(typeof color === "string") {
			s = "#" + color;
		} else { //typeof color === "number"
			color %= colors.length;
			var red = (colors[color][0] * fg) || bg;
			var green = (colors[color][1] * fg) || bg;
			var blue = (colors[color][2] * fg) || bg;
			red = Math.round(red * 255);
			green = Math.round(green * 255);
			blue = Math.round(blue * 255);
			s = 'rgb(' + red + ', ' + green + ', ' + blue + ')';
		}
		this.ctx.strokeStyle = s;
		this.ctx.fillStyle = s;
		return s;

	},

	edge: function(x0, y0, x1, y1, color, width) {

		this.setColor(color, 0.0, 0.65);
		if(width >= 0)
			 this.ctx.lineWidth = width;
		this.ctx.beginPath();
		this.ctx.moveTo(x0, y0);
		this.ctx.lineTo(x1, y1);
		this.ctx.stroke();

	},

	graphNodeCurrent: function(x, y, radius) {
		this.ctx.lineWidth = 2;
		this.ctx.beginPath();
		this.ctx.arc(x, y, radius * 1.75, 0, Math.PI * 2, true);
		this.ctx.stroke();
	},

	graphNodeClosing: function(x, y, radius) {
		this.ctx.fillRect(x - radius, y - 1.5, radius * 2, 3);
	},

	graphNodeUnstable: function(x, y, radius) {
		var x30 = radius * Math.cos(Math.PI / 6);
		var y30 = radius * Math.sin(Math.PI / 6);
		this.ctx.lineWidth = 2;
		this.ctx.beginPath();
		this.ctx.moveTo(x, y - radius);
		this.ctx.lineTo(x, y + radius);
		this.ctx.moveTo(x - x30, y - y30);
		this.ctx.lineTo(x + x30, y + y30);
		this.ctx.moveTo(x - x30, y + y30);
		this.ctx.lineTo(x + x30, y - y30);
		this.ctx.stroke();
	},

	graphNodeObsolete: function(x, y, radius) {
		var p45 = radius * Math.cos(Math.PI / 4);
		this.ctx.lineWidth = 3;
		this.ctx.beginPath();
		this.ctx.moveTo(x - p45, y - p45);
		this.ctx.lineTo(x + p45, y + p45);
		this.ctx.moveTo(x - p45, y + p45);
		this.ctx.lineTo(x + p45, y - p45);
		this.ctx.stroke();
	},

	graphNodeNormal: function(x, y, radius) {
		this.ctx.beginPath();
		this.ctx.arc(x, y, radius, 0, Math.PI * 2, true);
		this.ctx.fill();
	},

	vertex: function(x, y, radius, color, parity, cur) {
		this.ctx.save();
		this.setColor(color, 0.25, 0.75);
		if (cur.graphnode[0] === '@') {
			this.graphNodeCurrent(x, y, radius);
		}
		switch (cur.graphnode.substr(-1)) {
			case '_':
				this.graphNodeClosing(x, y, radius);
				break;
			case '*':
				this.graphNodeUnstable(x, y, radius);
				break;
			case 'x':
				this.graphNodeObsolete(x, y, radius);
				break;
			default:
				this.graphNodeNormal(x, y, radius);
		}
		this.ctx.restore();

		var left = (this.bg_height - this.box_size) + (this.columns + 1) * this.box_size;
		var item = document.querySelector('[data-node="' + cur.node + '"]');
		if (item) {
			item.style.paddingLeft = left + 'px';
		}
	},

	render: function(data) {

		var i, j, cur, line, start, end, color, x, y, x0, y0, x1, y1, column, radius;

		var cols = 0;
		for (i = 0; i < data.length; i++) {
			cur = data[i];
			for (j = 0; j < cur.edges.length; j++) {
				line = cur.edges[j];
				cols = Math.max(cols, line[0], line[1]);
			}
		}
		this.canvas.width = (cols + 1) * this.bg_height;
		this.canvas.height = (data.length + 1) * this.bg_height - 27;

		for (i = 0; i < data.length; i++) {

			var parity = i % 2;
			this.cell[1] += this.bg_height;
			this.bg[1] += this.bg_height;

			cur = data[i];
			var fold = false;

			var prevWidth = this.ctx.lineWidth;
			for (j = 0; j < cur.edges.length; j++) {

				line = cur.edges[j];
				start = line[0];
				end = line[1];
				color = line[2];
				var width = line[3];
				if(width < 0)
					 width = prevWidth;
				var branchcolor = line[4];
				if(branchcolor)
					color = branchcolor;

				if (end > this.columns || start > this.columns) {
					this.columns += 1;
				}

				if (start === this.columns && start > end) {
					fold = true;
				}

				x0 = this.cell[0] + this.box_size * start + this.box_size / 2;
				y0 = this.bg[1] - this.bg_height / 2;
				x1 = this.cell[0] + this.box_size * end + this.box_size / 2;
				y1 = this.bg[1] + this.bg_height / 2;

				this.edge(x0, y0, x1, y1, color, width);

			}
			this.ctx.lineWidth = prevWidth;

			// Draw the revision node in the right column

			column = cur.vertex[0];
			color = cur.vertex[1];

			radius = this.box_size / 8;
			x = this.cell[0] + this.box_size * column + this.box_size / 2;
			y = this.bg[1] - this.bg_height / 2;
			this.vertex(x, y, radius, color, parity, cur);

			if (fold) this.columns -= 1;

		}

	}

};


function process_dates(parentSelector){

	// derived from code from mercurial/templatefilter.py

	var scales = {
		'year':  365 * 24 * 60 * 60,
		'month':  30 * 24 * 60 * 60,
		'week':    7 * 24 * 60 * 60,
		'day':    24 * 60 * 60,
		'hour':   60 * 60,
		'minute': 60,
		'second': 1
	};

	function format(count, string){
		var ret = count + ' ' + string;
		if (count > 1){
			ret = ret + 's';
		}
 		return ret;
 	}

	function shortdate(date){
		var ret = date.getFullYear() + '-';
		// getMonth() gives a 0-11 result
		var month = date.getMonth() + 1;
		if (month <= 9){
			ret += '0' + month;
		} else {
			ret += month;
		}
		ret += '-';
		var day = date.getDate();
		if (day <= 9){
			ret += '0' + day;
		} else {
			ret += day;
		}
		return ret;
	}

 	function age(datestr){
 		var now = new Date();
 		var once = new Date(datestr);
		if (isNaN(once.getTime())){
			// parsing error
			return datestr;
		}

		var delta = Math.floor((now.getTime() - once.getTime()) / 1000);

		var future = false;
		if (delta < 0){
			future = true;
			delta = -delta;
			if (delta > (30 * scales.year)){
				return "in the distant future";
			}
		}

		if (delta > (2 * scales.year)){
			return shortdate(once);
		}

		for (var unit in scales){
			if (!scales.hasOwnProperty(unit)) { continue; }
			var s = scales[unit];
			var n = Math.floor(delta / s);
			if ((n >= 2) || (s === 1)){
				if (future){
					return format(n, unit) + ' from now';
				} else {
					return format(n, unit) + ' ago';
				}
			}
		}
	}

	var nodes = document.querySelectorAll((parentSelector || '') + ' .age');
	var dateclass = new RegExp('\\bdate\\b');
	for (var i=0; i<nodes.length; ++i){
		var node = nodes[i];
		var classes = node.className;
		var agevalue = age(node.textContent);
		if (dateclass.test(classes)){
			// We want both: date + (age)
			node.textContent += ' ('+agevalue+')';
		} else {
			node.title = node.textContent;
			node.textContent = agevalue;
		}
	}
}

function toggleDiffstat(event) {
    var curdetails = document.getElementById('diffstatdetails').style.display;
    var curexpand = curdetails === 'none' ? 'inline' : 'none';
    document.getElementById('diffstatdetails').style.display = curexpand;
    document.getElementById('diffstatexpand').style.display = curdetails;
    event.preventDefault();
}

function toggleLinewrap(event) {
    function getLinewrap() {
        var nodes = document.getElementsByClassName('sourcelines');
        // if there are no such nodes, error is thrown here
        return nodes[0].classList.contains('wrap');
    }

    function setLinewrap(enable) {
        var nodes = document.getElementsByClassName('sourcelines');
        var i;
        for (i = 0; i < nodes.length; i++) {
            if (enable) {
                nodes[i].classList.add('wrap');
            } else {
                nodes[i].classList.remove('wrap');
            }
        }

        var links = document.getElementsByClassName('linewraplink');
        for (i = 0; i < links.length; i++) {
            links[i].innerHTML = enable ? 'on' : 'off';
        }
    }

    setLinewrap(!getLinewrap());
    event.preventDefault();
}

function format(str, replacements) {
    return str.replace(/%(\w+)%/g, function(match, p1) {
        return String(replacements[p1]);
    });
}

function makeRequest(url, method, onstart, onsuccess, onerror, oncomplete) {
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
        if (xhr.readyState === 4) {
            try {
                if (xhr.status === 200) {
                    onsuccess(xhr.responseText);
                } else {
                    throw 'server error';
                }
            } catch (e) {
                onerror(e);
            } finally {
                oncomplete();
            }
        }
    };

    xhr.open(method, url);
    xhr.overrideMimeType("text/xhtml; charset=" + document.characterSet.toLowerCase());
    xhr.send();
    onstart();
    return xhr;
}

function removeByClassName(className) {
    var nodes = document.getElementsByClassName(className);
    while (nodes.length) {
        nodes[0].parentNode.removeChild(nodes[0]);
    }
}

function docFromHTML(html) {
    var doc = document.implementation.createHTMLDocument('');
    doc.documentElement.innerHTML = html;
    return doc;
}

function appendFormatHTML(element, formatStr, replacements) {
    element.insertAdjacentHTML('beforeend', format(formatStr, replacements));
}

function adoptChildren(from, to) {
    var nodes = from.children;
    var curClass = 'c' + Date.now();
    while (nodes.length) {
        var node = nodes[0];
        node = document.adoptNode(node);
        node.classList.add(curClass);
        to.appendChild(node);
    }
    process_dates('.' + curClass);
}

function ajaxScrollInit(urlFormat,
                        nextPageVar,
                        nextPageVarGet,
                        containerSelector,
                        messageFormat,
                        mode) {
    var updateInitiated = false;
    var container = document.querySelector(containerSelector);

    function scrollHandler() {
        if (updateInitiated) {
            return;
        }

        var scrollHeight = document.documentElement.scrollHeight;
        var clientHeight = document.documentElement.clientHeight;
        var scrollTop = document.body.scrollTop || document.documentElement.scrollTop;

        if (scrollHeight - (scrollTop + clientHeight) < 50) {
            updateInitiated = true;
            removeByClassName('scroll-loading-error');
            container.lastElementChild.classList.add('scroll-separator');

            if (!nextPageVar) {
                var message = {
                    'class': 'scroll-loading-info',
                    text: 'No more entries'
                };
                appendFormatHTML(container, messageFormat, message);
                return;
            }

            makeRequest(
                format(urlFormat, {next: nextPageVar}),
                'GET',
                function onstart() {
                    var message = {
                        'class': 'scroll-loading',
                        text: 'Loading...'
                    };
                    appendFormatHTML(container, messageFormat, message);
                },
                function onsuccess(htmlText) {
                    var doc = docFromHTML(htmlText);

                    if (mode === 'graph') {
                        var graph = window.graph;
                        var dataStr = htmlText.match(/^\s*var data = (.*);$/m)[1];
                        var data = JSON.parse(dataStr);
                        graph.reset();
                        adoptChildren(doc.querySelector('#graphnodes'), container.querySelector('#graphnodes'));
                        graph.render(data);
                    } else {
                        adoptChildren(doc.querySelector(containerSelector), container);
                    }

                    nextPageVar = nextPageVarGet(htmlText);
                },
                function onerror(errorText) {
                    var message = {
                        'class': 'scroll-loading-error',
                        text: 'Error: ' + errorText
                    };
                    appendFormatHTML(container, messageFormat, message);
                },
                function oncomplete() {
                    removeByClassName('scroll-loading');
                    updateInitiated = false;
                    scrollHandler();
                }
            );
        }
    }

    window.addEventListener('scroll', scrollHandler);
    window.addEventListener('resize', scrollHandler);
    scrollHandler();
}

function renderDiffOptsForm() {
    // We use URLSearchParams for query string manipulation. Old browsers don't
    // support this API.
    if (!("URLSearchParams" in window)) {
        return;
    }

    var form = document.getElementById("diffopts-form");

    var KEYS = [
        "ignorews",
        "ignorewsamount",
        "ignorewseol",
        "ignoreblanklines",
    ];

    var urlParams = new window.URLSearchParams(window.location.search);

    function updateAndRefresh(e) {
        var checkbox = e.target;
        var name = checkbox.id.substr(0, checkbox.id.indexOf("-"));
        urlParams.set(name, checkbox.checked ? "1" : "0");
        window.location.search = urlParams.toString();
    }

    var allChecked = form.getAttribute("data-ignorews") === "1";

    for (var i = 0; i < KEYS.length; i++) {
        var key = KEYS[i];

        var checkbox = document.getElementById(key + "-checkbox");
        if (!checkbox) {
            continue;
        }

        var currentValue = form.getAttribute("data-" + key);
        checkbox.checked = currentValue !== "0";

        // ignorews implies ignorewsamount and ignorewseol.
        if (allChecked && (key === "ignorewsamount" || key === "ignorewseol")) {
            checkbox.checked = true;
            checkbox.disabled = true;
        }

        checkbox.addEventListener("change", updateAndRefresh, false);
    }

    form.style.display = 'block';
}

function addDiffStatToggle() {
    var els = document.getElementsByClassName("diffstattoggle");

    for (var i = 0; i < els.length; i++) {
        els[i].addEventListener("click", toggleDiffstat, false);
    }
}

function addLineWrapToggle() {
    var els = document.getElementsByClassName("linewraptoggle");

    for (var i = 0; i < els.length; i++) {
        var nodes = els[i].getElementsByClassName("linewraplink");

        for (var j = 0; j < nodes.length; j++) {
            nodes[j].addEventListener("click", toggleLinewrap, false);
        }
    }
}

document.addEventListener('DOMContentLoaded', function() {
   process_dates();
   addDiffStatToggle();
   addLineWrapToggle();
}, false);
